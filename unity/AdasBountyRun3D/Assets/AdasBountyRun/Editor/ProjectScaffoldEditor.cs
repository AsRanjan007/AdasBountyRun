using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using ABR.Core;
using ABR.Vehicle;

namespace ABR.Editor
{
    /// <summary>
    /// Build step 1 automation. Creates the URP pipeline asset, assigns it, and
    /// generates the default ScriptableObject config assets (vehicle + surfaces +
    /// database). Anything that can't be automated on a given Unity version is
    /// logged with the exact manual fallback (see MANUAL_STEPS.md).
    /// </summary>
    public static class ProjectScaffoldEditor
    {
        public const string ConfigDir = "Assets/AdasBountyRun/_GeneratedConfigs";
        public const string RenderDir = "Assets/AdasBountyRun/_GeneratedConfigs/Rendering";
        public const string SurfaceDbPath = ConfigDir + "/SurfaceDatabase.asset";
        public const string VehiclePath = ConfigDir + "/DefaultVehicleProfile.asset";

        [MenuItem("ABR/Setup/1 · Scaffold URP + Configs")]
        public static void Scaffold()
        {
            EnsureFolder(ConfigDir);
            EnsureFolder(RenderDir);
            CreateUrpAsset();
            CreateVehicleProfile();
            CreateSurfaceDatabase();
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("[ABR] Scaffold complete. Next: ABR ▸ Setup ▸ 3 · Build Physics Test Scene.");
        }

        private static void CreateUrpAsset()
        {
            try
            {
                // ASSUMPTION: URP 12–17 expose UniversalRenderPipelineAsset.Create.
                var rendererData = ScriptableObject.CreateInstance<UniversalRendererData>();
                string rdPath = RenderDir + "/ABR_Renderer.asset";
                AssetDatabase.CreateAsset(rendererData, rdPath);

                var urp = UniversalRenderPipelineAsset.Create(rendererData);
                string urpPath = RenderDir + "/ABR_URP.asset";
                AssetDatabase.CreateAsset(urp, urpPath);

                GraphicsSettings.defaultRenderPipeline = urp;
                QualitySettings.renderPipeline = urp;
                Debug.Log("[ABR] URP asset created and assigned: " + urpPath);
            }
            catch (System.Exception e)
            {
                Debug.LogWarning("[ABR] Could not auto-create the URP asset (" + e.Message +
                    "). MANUAL: Assets ▸ Create ▸ Rendering ▸ URP Asset (with Universal Renderer), then " +
                    "assign it in Project Settings ▸ Graphics ▸ Scriptable Render Pipeline Settings and in " +
                    "each Quality level. See MANUAL_STEPS.md.");
            }
        }

        private static void CreateVehicleProfile()
        {
            if (AssetDatabase.LoadAssetAtPath<VehicleProfile>(VehiclePath) != null) return;
            var vp = ScriptableObject.CreateInstance<VehicleProfile>();
            AssetDatabase.CreateAsset(vp, VehiclePath);
        }

        private static void CreateSurfaceDatabase()
        {
            if (AssetDatabase.LoadAssetAtPath<SurfaceDatabase>(SurfaceDbPath) != null) return;
            var db = ScriptableObject.CreateInstance<SurfaceDatabase>();

            db.profiles.Add(MakeSurface(SurfaceType.Asphalt, grip: 1.00f, rr: 0.015f, fs: 1.00f, ss: 1.00f, rough: 0.05f, wet: 0.35f));
            db.profiles.Add(MakeSurface(SurfaceType.WetAsphalt, grip: 0.78f, rr: 0.020f, fs: 0.90f, ss: 0.85f, rough: 0.08f, wet: 0.15f));
            db.profiles.Add(MakeSurface(SurfaceType.Gravel, grip: 0.62f, rr: 0.045f, fs: 0.80f, ss: 0.70f, rough: 0.55f, wet: 0.25f));
            db.profiles.Add(MakeSurface(SurfaceType.Dirt, grip: 0.55f, rr: 0.055f, fs: 0.75f, ss: 0.65f, rough: 0.6f, wet: 0.5f));
            db.profiles.Add(MakeSurface(SurfaceType.Pothole, grip: 0.5f, rr: 0.09f, fs: 0.7f, ss: 0.6f, rough: 0.9f, wet: 0.3f));
            db.profiles.Add(MakeSurface(SurfaceType.SpeedBreaker, grip: 0.9f, rr: 0.03f, fs: 0.95f, ss: 0.9f, rough: 0.7f, wet: 0.3f));
            db.profiles.Add(MakeSurface(SurfaceType.Sand, grip: 0.45f, rr: 0.11f, fs: 0.6f, ss: 0.5f, rough: 0.5f, wet: 0.2f));
            db.profiles.Add(MakeSurface(SurfaceType.Ice, grip: 0.22f, rr: 0.01f, fs: 0.4f, ss: 0.35f, rough: 0.02f, wet: 0.1f));

            AssetDatabase.CreateAsset(db, SurfaceDbPath);
            foreach (var p in db.profiles) AssetDatabase.AddObjectToAsset(p, db);
            EditorUtility.SetDirty(db);
        }

        private static SurfaceProfile MakeSurface(SurfaceType type, float grip, float rr, float fs, float ss, float rough, float wet)
        {
            var s = ScriptableObject.CreateInstance<SurfaceProfile>();
            s.name = type.ToString();
            s.type = type; s.grip = grip; s.rollingResistance = rr;
            s.forwardStiffness = fs; s.sidewaysStiffness = ss; s.roughness = rough; s.wetnessSensitivity = wet;
            return s;
        }

        private static void EnsureFolder(string path)
        {
            if (AssetDatabase.IsValidFolder(path)) return;
            string parent = Path.GetDirectoryName(path).Replace('\\', '/');
            string leaf = Path.GetFileName(path);
            if (!AssetDatabase.IsValidFolder(parent)) EnsureFolder(parent);
            AssetDatabase.CreateFolder(parent, leaf);
        }
    }
}
