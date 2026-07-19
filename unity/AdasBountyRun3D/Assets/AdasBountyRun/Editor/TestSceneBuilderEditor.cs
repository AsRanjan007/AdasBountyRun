using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using ABR.Core;
using ABR.Vehicle;

namespace ABR.Editor
{
    /// <summary>
    /// Build step 1/2 automation: constructs the vehicle FEEL test scene entirely
    /// from script — flat asphalt with wet / gravel / pothole / speed-breaker
    /// patches (each SurfaceMarker'd so handling changes as you drive over them),
    /// an EnvironmentState (wetness), a chase camera, and the wired vehicle rig.
    /// Saves to Assets/AdasBountyRun/Scenes/PhysicsTest.unity.
    /// </summary>
    public static class TestSceneBuilderEditor
    {
        private const string ScenesDir = "Assets/AdasBountyRun/Scenes";
        private const string ScenePath = ScenesDir + "/PhysicsTest.unity";

        [MenuItem("ABR/Setup/3 · Build Physics Test Scene")]
        public static void Build()
        {
            if (AssetDatabase.LoadAssetAtPath<SurfaceDatabase>(ProjectScaffoldEditor.SurfaceDbPath) == null)
            {
                if (EditorUtility.DisplayDialog("ABR", "Configs not found. Run step 1 (Scaffold) first?", "Run it now", "Cancel"))
                    ProjectScaffoldEditor.Scaffold();
                else return;
            }

            var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);

            // Lighting.
            var sun = new GameObject("Directional Light").AddComponent<Light>();
            sun.type = LightType.Directional;
            sun.intensity = 1.1f;
            sun.transform.rotation = Quaternion.Euler(50f, -30f, 0f);
            RenderSettings.ambientMode = UnityEngine.Rendering.AmbientMode.Trilight;

            // Environment coupling parameter.
            var env = new GameObject("EnvironmentState").AddComponent<EnvironmentState>();

            // Ground + surface patches along +Z.
            MakeSurfacePatch("Ground_Asphalt", new Vector3(0f, -0.1f, 60f), new Vector3(40f, 0.2f, 240f), SurfaceType.Asphalt, new Color(0.22f, 0.22f, 0.24f));
            MakeSurfacePatch("Patch_Wet", new Vector3(0f, -0.09f, 60f), new Vector3(6f, 0.02f, 30f), SurfaceType.WetAsphalt, new Color(0.15f, 0.16f, 0.2f));
            MakeSurfacePatch("Patch_Gravel", new Vector3(9f, -0.05f, 95f), new Vector3(8f, 0.12f, 40f), SurfaceType.Gravel, new Color(0.5f, 0.45f, 0.36f));
            MakeSurfacePatch("Patch_Sand", new Vector3(-9f, -0.05f, 95f), new Vector3(8f, 0.12f, 40f), SurfaceType.Sand, new Color(0.72f, 0.62f, 0.42f));
            MakeSurfacePatch("Patch_Ice", new Vector3(0f, -0.08f, 150f), new Vector3(10f, 0.04f, 30f), SurfaceType.Ice, new Color(0.7f, 0.82f, 0.9f));

            // Pothole field (dips) + speed breaker (raised bar) — physical jolts.
            MakePotholeField(new Vector3(0f, 0f, 40f));
            MakeSpeedBreaker(new Vector3(0f, 0f, 25f));

            // Chase camera.
            var camGo = new GameObject("ChaseCamera");
            var cam = camGo.AddComponent<Camera>();
            camGo.AddComponent<AudioListener>();
            var chase = camGo.AddComponent<ChaseCamera>();

            // Vehicle rig at the spawn point.
            var vehicle = VehicleRigBuilderEditor.BuildVehicle(new Vector3(0f, 0.6f, 0f));
            var body = vehicle.GetComponent<Rigidbody>();
            chase.SetTarget(vehicle.transform, body);

            // Wire the feel HUD's environment reference.
            var hud = vehicle.GetComponent<FeelDebugHud>();
            if (hud != null)
            {
                var so = new SerializedObject(hud);
                so.FindProperty("environment").objectReferenceValue = env;
                so.ApplyModifiedPropertiesWithoutUndo();
            }

            EnsureScenesFolder();
            EditorSceneManager.SaveScene(scene, ScenePath);
            Debug.Log("[ABR] Physics test scene built: " + ScenePath +
                "  ▶ Press Play and drive with W/A/S/D. Z/X change wetness.");
        }

        private static GameObject MakeSurfacePatch(string name, Vector3 pos, Vector3 size, SurfaceType type, Color color)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            go.transform.position = pos;
            go.transform.localScale = size;
            go.AddComponent<SurfaceMarker>().surface = type;
            TintUnlit(go, color);
            return go;
        }

        private static void MakePotholeField(Vector3 origin)
        {
            var parent = new GameObject("PotholeField");
            parent.transform.position = origin;
            var rnd = new System.Random(7);
            for (int i = 0; i < 10; i++)
            {
                var pit = GameObject.CreatePrimitive(PrimitiveType.Cube);
                pit.name = "Pothole_" + i;
                pit.transform.SetParent(parent.transform, false);
                float x = (float)(rnd.NextDouble() * 6f - 3f);
                float z = (float)(rnd.NextDouble() * 8f);
                pit.transform.localPosition = new Vector3(x, -0.18f, z);   // recessed dip
                pit.transform.localScale = new Vector3(1.1f, 0.16f, 1.1f);
                pit.AddComponent<SurfaceMarker>().surface = SurfaceType.Pothole;
                TintUnlit(pit, new Color(0.08f, 0.08f, 0.09f));
            }
        }

        private static void MakeSpeedBreaker(Vector3 pos)
        {
            var bar = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            bar.name = "SpeedBreaker";
            bar.transform.position = pos + new Vector3(0f, 0.02f, 0f);
            bar.transform.rotation = Quaternion.Euler(0f, 0f, 90f);
            bar.transform.localScale = new Vector3(0.35f, 6f, 0.35f); // long low bar across the road
            bar.AddComponent<SurfaceMarker>().surface = SurfaceType.SpeedBreaker;
            TintUnlit(bar, new Color(0.85f, 0.75f, 0.1f));
        }

        // ASSUMPTION: a default URP/Lit material tints fine; we set color via a
        // MaterialPropertyBlock-free shared material to keep the scene self-contained.
        private static void TintUnlit(GameObject go, Color c)
        {
            var mr = go.GetComponent<MeshRenderer>();
            if (mr == null) return;
            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            if (shader == null) return;
            var mat = new Material(shader);
            mat.color = c;
            mr.sharedMaterial = mat;
        }

        private static void EnsureScenesFolder()
        {
            if (!AssetDatabase.IsValidFolder(ScenesDir))
                AssetDatabase.CreateFolder("Assets/AdasBountyRun", "Scenes");
        }
    }
}
