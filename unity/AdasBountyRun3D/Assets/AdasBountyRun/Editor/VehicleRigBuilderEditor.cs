using UnityEditor;
using UnityEngine;
using ABR.Core;
using ABR.Vehicle;

namespace ABR.Editor
{
    /// <summary>
    /// Build step 2 automation. Constructs a fully wired vehicle with a
    /// car-SHAPED placeholder body (hood + cabin + visible wheels + lights) and a
    /// COCKPIT interior (dashboard, turning steering wheel, windshield frame) plus a
    /// driver-eye anchor for the in-cabin camera. All from primitives — swap for a
    /// real car model later (the WheelColliders and anchors stay; replace the meshes).
    /// </summary>
    public static class VehicleRigBuilderEditor
    {
        // Car colours (placeholder).
        private static readonly Color BodyColor = new Color(0.13f, 0.32f, 0.62f);
        private static readonly Color CabinColor = new Color(0.10f, 0.24f, 0.48f);
        private static readonly Color GlassColor = new Color(0.15f, 0.22f, 0.30f);
        private static readonly Color TireColor = new Color(0.07f, 0.07f, 0.08f);
        private static readonly Color HubColor = new Color(0.55f, 0.57f, 0.60f);
        private static readonly Color TrimColor = new Color(0.10f, 0.11f, 0.12f);
        private static readonly Color DashColor = new Color(0.09f, 0.09f, 0.10f);

        [MenuItem("ABR/Setup/2 · Build Vehicle Rig (at origin)")]
        public static void BuildAtOrigin()
        {
            var go = BuildVehicle(new Vector3(0f, 0.7f, 0f));
            Selection.activeGameObject = go;
            EditorGUIUtility.PingObject(go);
        }

        public static GameObject BuildVehicle(Vector3 position)
        {
            var profile = AssetDatabase.LoadAssetAtPath<VehicleProfile>(ProjectScaffoldEditor.VehiclePath);
            var db = AssetDatabase.LoadAssetAtPath<SurfaceDatabase>(ProjectScaffoldEditor.SurfaceDbPath);
            if (profile == null)
                Debug.LogWarning("[ABR] No VehicleProfile — run ABR ▸ Setup ▸ 1 first. Using runtime defaults.");

            float wb = profile != null ? profile.wheelbase : 2.6f;
            float track = profile != null ? profile.trackWidth : 1.55f;
            float radius = profile != null ? profile.wheelRadius : 0.34f;
            float bodyW = track - 0.10f;                 // wheels stick out past the body

            var root = new GameObject("PlayerVehicle");
            root.transform.position = position;

            // Chassis collider (tuned; independent of the visual meshes).
            var col = root.AddComponent<BoxCollider>();
            col.center = new Vector3(0f, 0.34f, 0f);
            col.size = new Vector3(bodyW, 0.5f, wb + 0.5f);

            var rb = root.AddComponent<Rigidbody>();
            rb.mass = profile != null ? profile.mass : 1350f;
            rb.interpolation = RigidbodyInterpolation.Interpolate;
            rb.collisionDetectionMode = CollisionDetectionMode.ContinuousDynamic;
            if (profile != null) rb.centerOfMass = profile.centerOfMassOffset;

            var controller = root.AddComponent<VehicleController>();
            controller.profile = profile;
            controller.surfaceDatabase = db;
            root.AddComponent<CompositeVehicleInput>();

            BuildCarBody(root.transform, bodyW, wb, radius);
            BuildCockpit(root.transform, controller, bodyW, wb);
            var wheels = BuildWheels(root.transform, track, wb, radius, profile);
            AssignDrive(wheels, profile);
            controller.wheels = wheels;

            var hud = root.AddComponent<FeelDebugHud>();
            var so = new SerializedObject(hud);
            so.FindProperty("vehicle").objectReferenceValue = controller;
            so.ApplyModifiedPropertiesWithoutUndo();

            return root;
        }

        // ---------------- Car-shaped body ----------------

        private static void BuildCarBody(Transform root, float bodyW, float wb, float radius)
        {
            var body = new GameObject("Body");
            body.transform.SetParent(root, false);

            // Lower chassis.
            AddBox(body.transform, "Chassis", new Vector3(0f, 0.34f, 0f),
                new Vector3(bodyW, 0.34f, wb + 0.3f), BodyColor);
            // Hood (front, lower).
            AddBox(body.transform, "Hood", new Vector3(0f, 0.42f, wb * 0.42f),
                new Vector3(bodyW * 0.92f, 0.18f, wb * 0.34f), BodyColor);
            // Boot (rear, lower).
            AddBox(body.transform, "Boot", new Vector3(0f, 0.44f, -wb * 0.44f),
                new Vector3(bodyW * 0.92f, 0.2f, wb * 0.24f), BodyColor);
            // Cabin (raised, set back a touch).
            AddBox(body.transform, "Cabin", new Vector3(0f, 0.72f, -wb * 0.04f),
                new Vector3(bodyW * 0.86f, 0.42f, wb * 0.5f), CabinColor);
            // Roof.
            AddBox(body.transform, "Roof", new Vector3(0f, 0.94f, -wb * 0.04f),
                new Vector3(bodyW * 0.8f, 0.06f, wb * 0.46f), CabinColor);
            // Windshield + rear glass (tilted dark panels).
            var ws = AddBox(body.transform, "Windshield", new Vector3(0f, 0.74f, wb * 0.22f),
                new Vector3(bodyW * 0.82f, 0.42f, 0.04f), GlassColor);
            ws.transform.localRotation = Quaternion.Euler(28f, 0f, 0f);
            var rg = AddBox(body.transform, "RearGlass", new Vector3(0f, 0.78f, -wb * 0.3f),
                new Vector3(bodyW * 0.8f, 0.34f, 0.04f), GlassColor);
            rg.transform.localRotation = Quaternion.Euler(-32f, 0f, 0f);

            // Headlights (bright) + tail lights (red).
            float hx = bodyW * 0.34f;
            AddBox(body.transform, "Headlight_L", new Vector3(-hx, 0.42f, wb * 0.6f), new Vector3(0.22f, 0.1f, 0.05f), new Color(1f, 0.96f, 0.8f), emissive: true);
            AddBox(body.transform, "Headlight_R", new Vector3(hx, 0.42f, wb * 0.6f), new Vector3(0.22f, 0.1f, 0.05f), new Color(1f, 0.96f, 0.8f), emissive: true);
            AddBox(body.transform, "Taillight_L", new Vector3(-hx, 0.46f, -wb * 0.56f), new Vector3(0.22f, 0.1f, 0.05f), new Color(0.8f, 0.05f, 0.05f), emissive: true);
            AddBox(body.transform, "Taillight_R", new Vector3(hx, 0.46f, -wb * 0.56f), new Vector3(0.22f, 0.1f, 0.05f), new Color(0.8f, 0.05f, 0.05f), emissive: true);
            // Front grille / bumper.
            AddBox(body.transform, "Bumper", new Vector3(0f, 0.3f, wb * 0.61f), new Vector3(bodyW * 0.96f, 0.16f, 0.06f), TrimColor);
        }

        // ---------------- Cockpit interior ----------------

        private static void BuildCockpit(Transform root, VehicleController controller, float bodyW, float wb)
        {
            var interior = new GameObject("Interior");
            interior.transform.SetParent(root, false);

            // Driver on the left (LHD placeholder). Country-specific side comes later.
            float driverX = -bodyW * 0.24f;

            // Dashboard across the cabin front.
            AddBox(interior.transform, "Dashboard", new Vector3(0f, 0.56f, wb * 0.16f),
                new Vector3(bodyW * 0.82f, 0.14f, 0.28f), DashColor);
            // Instrument binnacle in front of the driver.
            AddBox(interior.transform, "Cluster", new Vector3(driverX, 0.62f, wb * 0.12f),
                new Vector3(0.34f, 0.1f, 0.16f), new Color(0.04f, 0.05f, 0.06f), emissive: true, emissionColor: new Color(0.05f, 0.15f, 0.2f));

            // Steering column + wheel (turns with input).
            var column = new GameObject("SteeringColumn");
            column.transform.SetParent(interior.transform, false);
            column.transform.localPosition = new Vector3(driverX, 0.52f, wb * 0.06f);
            column.transform.localRotation = Quaternion.Euler(22f, 0f, 0f); // lean back toward driver

            var rim = AddCylinder(column.transform, "SteeringRim", Vector3.zero,
                new Vector3(0.34f, 0.04f, 0.34f), TrimColor);
            rim.transform.localRotation = Quaternion.Euler(90f, 0f, 0f);    // disc faces the driver
            AddCylinder(column.transform, "SteeringHub", Vector3.zero,
                new Vector3(0.12f, 0.05f, 0.12f), new Color(0.15f, 0.16f, 0.18f))
                .transform.localRotation = Quaternion.Euler(90f, 0f, 0f);

            var steer = rim.AddComponent<SteeringWheelVisual>();
            var so = new SerializedObject(steer);
            so.FindProperty("vehicle").objectReferenceValue = controller;
            so.FindProperty("rim").objectReferenceValue = rim.transform;
            so.ApplyModifiedPropertiesWithoutUndo();

            // A-pillars framing the windshield (adds the "sitting inside" feel).
            AddBox(interior.transform, "APillar_L", new Vector3(-bodyW * 0.4f, 0.78f, wb * 0.2f), new Vector3(0.06f, 0.5f, 0.06f), TrimColor)
                .transform.localRotation = Quaternion.Euler(26f, 0f, 8f);
            AddBox(interior.transform, "APillar_R", new Vector3(bodyW * 0.4f, 0.78f, wb * 0.2f), new Vector3(0.06f, 0.5f, 0.06f), TrimColor)
                .transform.localRotation = Quaternion.Euler(26f, 0f, -8f);
            // Rear-view mirror.
            AddBox(interior.transform, "Mirror", new Vector3(0f, 0.98f, wb * 0.12f), new Vector3(0.24f, 0.06f, 0.03f), TrimColor);

            // Driver-eye anchor for the cockpit camera.
            var anchor = new GameObject("CockpitAnchor");
            anchor.transform.SetParent(root, false);
            anchor.transform.localPosition = new Vector3(driverX, 0.82f, -wb * 0.02f);
            anchor.transform.localRotation = Quaternion.identity; // looks along the car's +Z
        }

        // ---------------- Wheels ----------------

        private static WheelUnit[] BuildWheels(Transform root, float track, float wb, float radius, VehicleProfile p)
        {
            float ht = track * 0.5f, hb = wb * 0.5f;
            var wheels = new WheelUnit[4];
            wheels[0] = MakeWheel(root, "Wheel_FL", new Vector3(-ht, 0f, hb), radius, p, steer: true);
            wheels[1] = MakeWheel(root, "Wheel_FR", new Vector3(ht, 0f, hb), radius, p, steer: true);
            wheels[2] = MakeWheel(root, "Wheel_RL", new Vector3(-ht, 0f, -hb), radius, p, steer: false);
            wheels[3] = MakeWheel(root, "Wheel_RR", new Vector3(ht, 0f, -hb), radius, p, steer: false);
            return wheels;
        }

        private static void AssignDrive(WheelUnit[] wheels, VehicleProfile p)
        {
            var drive = p != null ? p.driveType : DriveType.RearWheel;
            for (int i = 0; i < 4; i++)
            {
                bool front = i < 2;
                wheels[i].driven = drive == DriveType.AllWheel ||
                                   (drive == DriveType.FrontWheel && front) ||
                                   (drive == DriveType.RearWheel && !front);
                wheels[i].handbrakeAffected = !front;
            }
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

            // Visual: tyre (dark cylinder) + hubcap.
            var tyre = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            tyre.name = name + "_Tyre";
            Object.DestroyImmediate(tyre.GetComponent<CapsuleCollider>());
            tyre.transform.SetParent(wheelGo.transform, false);
            tyre.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
            tyre.transform.localScale = new Vector3(radius * 2f, 0.14f, radius * 2f);
            Colorize(tyre, TireColor);

            var hub = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            hub.name = name + "_Hub";
            Object.DestroyImmediate(hub.GetComponent<CapsuleCollider>());
            hub.transform.SetParent(wheelGo.transform, false);
            hub.transform.localRotation = Quaternion.Euler(0f, 0f, 90f);
            hub.transform.localScale = new Vector3(radius * 1.05f, 0.16f, radius * 1.05f);
            Colorize(hub, HubColor);

            return new WheelUnit
            {
                collider = wc,
                visual = tyre.transform,
                steerable = steer,
                driven = false,
                handbrakeAffected = false
            };
        }

        // ---------------- Primitive helpers ----------------

        private static GameObject AddBox(Transform parent, string name, Vector3 localPos, Vector3 size, Color color, bool emissive = false, Color? emissionColor = null)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cube);
            go.name = name;
            Object.DestroyImmediate(go.GetComponent<BoxCollider>()); // visual only
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localScale = size;
            Colorize(go, color, emissive, emissionColor);
            return go;
        }

        private static GameObject AddCylinder(Transform parent, string name, Vector3 localPos, Vector3 size, Color color)
        {
            var go = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            go.name = name;
            Object.DestroyImmediate(go.GetComponent<CapsuleCollider>());
            go.transform.SetParent(parent, false);
            go.transform.localPosition = localPos;
            go.transform.localScale = size;
            Colorize(go, color);
            return go;
        }

        private static void Colorize(GameObject go, Color color, bool emissive = false, Color? emissionColor = null)
        {
            var mr = go.GetComponent<MeshRenderer>();
            if (mr == null) return;
            var shader = Shader.Find("Universal Render Pipeline/Lit") ?? Shader.Find("Standard");
            if (shader == null) return;
            var mat = new Material(shader) { color = color };
            mat.SetFloat("_Smoothness", 0.35f);
            if (emissive)
            {
                mat.EnableKeyword("_EMISSION");
                mat.SetColor("_EmissionColor", emissionColor ?? color);
            }
            mr.sharedMaterial = mat;
        }
    }
}
