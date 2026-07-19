using UnityEditor;
using UnityEngine;
using ABR.Core;
using ABR.Vehicle;

namespace ABR.Editor
{
    /// <summary>
    /// Build step 2 automation. Constructs a fully wired vehicle: Rigidbody + 4
    /// WheelColliders positioned from the profile, placeholder body/wheel meshes,
    /// the controller, input and feel HUD. Everything editor-bound (collider
    /// placement, component wiring) happens here — no Inspector work required.
    /// Replace the placeholder cube/cylinder meshes with a real car model later.
    /// </summary>
    public static class VehicleRigBuilderEditor
    {
        [MenuItem("ABR/Setup/2 · Build Vehicle Rig (at origin)")]
        public static void BuildAtOrigin()
        {
            var go = BuildVehicle(new Vector3(0f, 0.6f, 0f));
            Selection.activeGameObject = go;
            EditorGUIUtility.PingObject(go);
        }

        public static GameObject BuildVehicle(Vector3 position)
        {
            var profile = AssetDatabase.LoadAssetAtPath<VehicleProfile>(ProjectScaffoldEditor.VehiclePath);
            var db = AssetDatabase.LoadAssetAtPath<SurfaceDatabase>(ProjectScaffoldEditor.SurfaceDbPath);
            if (profile == null)
                Debug.LogWarning("[ABR] No VehicleProfile found — run ABR ▸ Setup ▸ 1 first. Using runtime defaults.");

            float wb = profile != null ? profile.wheelbase : 2.6f;
            float track = profile != null ? profile.trackWidth : 1.55f;
            float radius = profile != null ? profile.wheelRadius : 0.34f;

            var root = new GameObject("PlayerVehicle");
            root.transform.position = position;

            // Body (placeholder mesh + collider).
            var body = GameObject.CreatePrimitive(PrimitiveType.Cube);
            body.name = "Body";
            Object.DestroyImmediate(body.GetComponent<BoxCollider>()); // add our own tuned one
            body.transform.SetParent(root.transform, false);
            body.transform.localScale = new Vector3(track + 0.3f, 0.55f, wb + 1.1f);
            body.transform.localPosition = new Vector3(0f, 0.35f, 0f);

            var col = root.AddComponent<BoxCollider>();
            col.center = new Vector3(0f, 0.35f, 0f);
            col.size = new Vector3(track + 0.2f, 0.55f, wb + 0.9f);

            var rb = root.AddComponent<Rigidbody>();
            rb.mass = profile != null ? profile.mass : 1350f;
            rb.interpolation = RigidbodyInterpolation.Interpolate;
            rb.collisionDetectionMode = CollisionDetectionMode.ContinuousDynamic;
            if (profile != null) rb.centerOfMass = profile.centerOfMassOffset;

            var controller = root.AddComponent<VehicleController>();
            controller.profile = profile;
            controller.surfaceDatabase = db;
            root.AddComponent<CompositeVehicleInput>();

            // Wheels: FL, FR, RL, RR.
            float halfTrack = track * 0.5f;
            float halfBase = wb * 0.5f;
            var wheels = new WheelUnit[4];
            wheels[0] = MakeWheel(root.transform, "Wheel_FL", new Vector3(-halfTrack, 0f, halfBase), radius, profile, steer: true);
            wheels[1] = MakeWheel(root.transform, "Wheel_FR", new Vector3(halfTrack, 0f, halfBase), radius, profile, steer: true);
            wheels[2] = MakeWheel(root.transform, "Wheel_RL", new Vector3(-halfTrack, 0f, -halfBase), radius, profile, steer: false);
            wheels[3] = MakeWheel(root.transform, "Wheel_RR", new Vector3(halfTrack, 0f, -halfBase), radius, profile, steer: false);

            // Drive/handbrake assignment from drive type.
            var drive = profile != null ? profile.driveType : DriveType.RearWheel;
            for (int i = 0; i < 4; i++)
            {
                bool front = i < 2;
                wheels[i].driven = drive == DriveType.AllWheel || (drive == DriveType.FrontWheel && front) ||
                                   (drive == DriveType.RearWheel && !front);
                wheels[i].handbrakeAffected = !front; // rear handbrake
            }
            controller.wheels = wheels;

            var hud = root.AddComponent<FeelDebugHud>();
            var so = new SerializedObject(hud);
            so.FindProperty("vehicle").objectReferenceValue = controller;
            so.ApplyModifiedPropertiesWithoutUndo();

            return root;
        }

        private static WheelUnit MakeWheel(Transform parent, string name, Vector3 localPos, float radius, VehicleProfile p, bool steer)
        {
            var wheelGo = new GameObject(name);
            wheelGo.transform.SetParent(parent, false);
            wheelGo.transform.localPosition = localPos + Vector3.up * radius;

            var wc = wheelGo.AddComponent<WheelCollider>();
            wc.radius = radius;
            wc.mass = p != null ? p.wheelMass : 20f;
            wc.suspensionDistance = p != null ? p.suspensionDistance : 0.18f;
            wc.forceAppPointDistance = p != null ? p.forceAppPointDistance : 0.05f;
            var spring = wc.suspensionSpring;
            spring.spring = p != null ? p.springStrength : 32000f;
            spring.damper = p != null ? p.damperStrength : 4500f;
            spring.targetPosition = p != null ? p.suspensionTarget : 0.5f;
            wc.suspensionSpring = spring;

            // Visual placeholder (cylinder rotated to lie like a wheel).
            var vis = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            vis.name = name + "_Visual";
            Object.DestroyImmediate(vis.GetComponent<CapsuleCollider>());
            vis.transform.SetParent(wheelGo.transform, false);
            vis.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
            vis.transform.localScale = new Vector3(radius * 2f, 0.12f, radius * 2f);

            return new WheelUnit
            {
                collider = wc,
                visual = vis.transform,
                steerable = steer,
                driven = false,
                handbrakeAffected = false
            };
        }
    }
}
