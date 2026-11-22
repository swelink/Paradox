package cc.swelink.renderer;

import cc.swelink.Paradox;
import cc.swelink.systems.modules.render.CapeModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

public final class CapeCloth {
    private static final int COLUMNS = 11;
    private static final int ROWS = 25;
    private static final int COUNT = COLUMNS * ROWS;
    private static final float WIDTH = 10;
    private static final float LENGTH = 16;
    private static final float SPACING_X = WIDTH / (COLUMNS - 1);
    private static final float SPACING_Y = LENGTH / (ROWS - 1);
    private static final float THICKNESS = 0.4f;
    private static final float HALF = THICKNESS / 2;
    private static final float TEXTURE_WIDTH = 64;
    private static final float TEXTURE_HEIGHT = 32;
    private static final float MODEL_SCALE = 0.9375f;
    private static final float PIXELS_PER_BLOCK = 16 / MODEL_SCALE;
    private static final float STANDING_NECK = 24 * MODEL_SCALE / 16;
    private static final float CROUCHING_NECK = STANDING_NECK - 0.25f;
    private static final float CHEST_SHIFT_Y = -0.053125f * 16;
    private static final float CHEST_SHIFT_Z = 0.06875f * 16;
    private static final float OUTER_ARMOR = 1;
    private static final float INNER_ARMOR = 0.5f;
    private static final float STEP = 1 / 120f;
    private static final float MAX_DELTA = 0.1f;
    private static final float GRAVITY = 9.81f * PIXELS_PER_BLOCK;
    private static final float NORMAL_DRAG = 0.22f;
    private static final float TANGENT_DRAG = 0.6f;
    private static final float DAMPING = 0.99f;
    private static final float TURBULENCE = 0.12f;
    private static final int ITERATIONS = 16;
    private static final float BEND_STIFFNESS = 0.25f;
    private static final float CORNER_RADIUS = 1.2f;
    private static final float CONTACT_FRICTION = 0.85f;
    private static final float WIDTH_BEND_STIFFNESS = 0.8f;
    private static final float TELEPORT = 4;
    private static final float VELOCITY_SMOOTHING = 12;
    private static final float MAX_ACCELERATION = 600;
    private static final float MAX_TURN = 25;
    private static final int PUSH_STEPS = 24;
    private static final float PUSH_STEP = 0.5f;
    private static final long FORGET_NANOS = 5_000_000_000L;
    private static final int[] LINKS = links();
    private static final Map<Integer, Cloth> CLOTHS = new HashMap<>();
    private static final Map<Integer, Cloth> PREVIEW_CLOTHS = new HashMap<>();
    private static boolean preview;

    private CapeCloth() {
    }

    public static boolean submit(SubmitNodeCollector collector, Model<?> model, AvatarRenderState state, PoseStack pose,
                                 RenderType type, int light, int overlay) {
        if (!(model instanceof PlayerCapeModel capeModel) || state.skin == null || state.skin.cape() == null) return false;
        var module = Paradox.MODULES.get(CapeModule.class);
        if (!module.isEnabled() || !module.physics() || !CapeTexture.owns(state.skin.cape().texturePath())) return false;
        capeModel.setupAnim(state);
        var root = capeModel.root();
        var body = root.getChild("body");
        var cloth = simulate(state, new Body(state, root, body));
        pose.pushPose();
        try {
            root.translateAndRotate(pose);
            body.translateAndRotate(pose);
            pose.translate(0, 0, 2 / 16f);
            var points = cloth.snapshot();
            collector.submitCustomGeometry(pose, type, (transform, vertices) -> mesh(transform, vertices, points, light, overlay,
                    module.quality()));
        } finally {
            pose.popPose();
        }
        return true;
    }

    private static int[] links() {
        var list = new java.util.ArrayList<int[]>();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                if (c + 1 < COLUMNS) list.add(new int[] {index(c, r), index(c + 1, r), 0});
                if (r + 1 < ROWS) list.add(new int[] {index(c, r), index(c, r + 1), 0});
                if (c + 1 < COLUMNS && r + 1 < ROWS) {
                    list.add(new int[] {index(c, r), index(c + 1, r + 1), 0});
                    list.add(new int[] {index(c + 1, r), index(c, r + 1), 0});
                }
                if (c + 2 < COLUMNS) list.add(new int[] {index(c, r), index(c + 2, r), 2});
                if (r + 2 < ROWS) list.add(new int[] {index(c, r), index(c, r + 2), 1});
            }
        }
        int[] links = new int[list.size() * 3];
        for (int i = 0; i < list.size(); i++) System.arraycopy(list.get(i), 0, links, i * 3, 3);
        return links;
    }

    private static int index(int column, int row) {
        return row * COLUMNS + column;
    }

    private static float restX(int i) {
        return -WIDTH / 2 + (i % COLUMNS) * SPACING_X;
    }

    private static float restY(int i) {
        return (i / COLUMNS) * SPACING_Y;
    }

    public static void preview(boolean active) {
        preview = active;
    }

    private static Cloth simulate(AvatarRenderState state, Body body) {
        long now = System.nanoTime();
        var cloths = preview ? PREVIEW_CLOTHS : CLOTHS;
        cloths.values().removeIf(cloth -> now - cloth.seen > FORGET_NANOS);
        var cloth = cloths.computeIfAbsent(state.id, id -> new Cloth());
        var position = new Vec3(state.x, state.y, state.z);
        float delta = Math.min(MAX_DELTA, (now - cloth.seen) * 1.0e-9f);
        if (cloth.position == null || cloth.position.distanceToSqr(position) > TELEPORT * TELEPORT) {
            cloth.reset(body);
            cloth.yaw = state.bodyRot;
            delta = 0;
        }
        var moved = cloth.position == null ? Vec3.ZERO : position.subtract(cloth.position);
        float turn = Math.clamp(Mth.wrapDegrees(state.bodyRot - cloth.yaw), -MAX_TURN, MAX_TURN);
        cloth.position = position;
        cloth.yaw = state.bodyRot;
        cloth.seen = now;
        double yaw = Math.toRadians(state.bodyRot);
        var back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
        var left = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        var world = new Vector3f((float) moved.dot(left), (float) -moved.y, (float) moved.dot(back)).mul(PIXELS_PER_BLOCK);
        var local = body.toCape(world);
        var gravity = body.toCape(new Vector3f(0, GRAVITY, 0));
        var frame = new Frame(Minecraft.getInstance().level, position, back, left, body,
                state.isCrouching ? CROUCHING_NECK : STANDING_NECK);
        cloth.step(delta, local, (float) Math.toRadians(turn), gravity, body, frame);
        return cloth;
    }

    private static void mesh(PoseStack.Pose pose, VertexConsumer vertices, float[] points, int light, int overlay,
                             int sub) {
        int fc = (COLUMNS - 1) * sub + 1;
        int fr = (ROWS - 1) * sub + 1;
        float[] fine = refine(points, sub);
        float[] normals = new float[fc * fr * 3];
        for (int r = 0; r < fr; r++) {
            for (int c = 0; c < fc; c++) {
                int i = fine(fc, c, r);
                int left = fine(fc, Math.max(0, c - 1), r);
                int right = fine(fc, Math.min(fc - 1, c + 1), r);
                int up = fine(fc, c, Math.max(0, r - 1));
                int down = fine(fc, c, Math.min(fr - 1, r + 1));
                float ax = fine[right * 3] - fine[left * 3];
                float ay = fine[right * 3 + 1] - fine[left * 3 + 1];
                float az = fine[right * 3 + 2] - fine[left * 3 + 2];
                float bx = fine[down * 3] - fine[up * 3];
                float by = fine[down * 3 + 1] - fine[up * 3 + 1];
                float bz = fine[down * 3 + 2] - fine[up * 3 + 2];
                float nx = ay * bz - az * by;
                float ny = az * bx - ax * bz;
                float nz = ax * by - ay * bx;
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (length < 1.0e-6f) {
                    nx = 0;
                    ny = 0;
                    nz = 1;
                    length = 1;
                }
                normals[i * 3] = nx / length;
                normals[i * 3 + 1] = ny / length;
                normals[i * 3 + 2] = nz / length;
            }
        }
        float[] outer = new float[fc * fr * 3];
        float[] inner = new float[fc * fr * 3];
        for (int i = 0; i < fc * fr; i++) {
            for (int a = 0; a < 3; a++) {
                outer[i * 3 + a] = fine[i * 3 + a] + normals[i * 3 + a] * HALF;
                inner[i * 3 + a] = fine[i * 3 + a] - normals[i * 3 + a] * HALF;
            }
        }
        float stepX = SPACING_X / sub;
        float stepY = SPACING_Y / sub;
        for (int r = 0; r < fr - 1; r++) {
            float vTop = 1 + r * stepY;
            float vBottom = 1 + (r + 1) * stepY;
            for (int c = 0; c < fc - 1; c++) {
                int[] corners = {fine(fc, c, r), fine(fc, c + 1, r), fine(fc, c + 1, r + 1), fine(fc, c, r + 1)};
                float uLeft = 11 - c * stepX;
                float uRight = 11 - (c + 1) * stepX;
                float[] outerUv = {uLeft, vTop, uRight, vTop, uRight, vBottom, uLeft, vBottom};
                float[] innerUv = {12 + c * stepX, vTop, 12 + (c + 1) * stepX, vTop,
                        12 + (c + 1) * stepX, vBottom, 12 + c * stepX, vBottom};
                quad(vertices, pose, outer, corners, outerUv, normals, 1, false, light, overlay);
                quad(vertices, pose, inner, corners, innerUv, normals, -1, true, light, overlay);
            }
            for (int side = 0; side < 2; side++) {
                int c = side == 0 ? 0 : fc - 1;
                edge(vertices, pose, outer, inner, fine(fc, c, r), fine(fc, c, r + 1), side == 0 ? 10.85f : 1.15f, vTop,
                        side == 0 ? 10.85f : 1.15f, vBottom, light, overlay);
            }
        }
        for (int end = 0; end < 2; end++) {
            int r = end == 0 ? 0 : fr - 1;
            float v = end == 0 ? 1.15f : 16.85f;
            for (int c = 0; c < fc - 1; c++) {
                edge(vertices, pose, outer, inner, fine(fc, c, r), fine(fc, c + 1, r), 11 - c * stepX, v,
                        11 - (c + 1) * stepX, v, light, overlay);
            }
        }
    }

    private static int fine(int fc, int column, int row) {
        return row * fc + column;
    }

    private static float[] refine(float[] points, int sub) {
        int fc = (COLUMNS - 1) * sub + 1;
        int fr = (ROWS - 1) * sub + 1;
        float[] across = new float[ROWS * fc * 3];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < fc; c++) {
                int cell = Math.min(c / sub, COLUMNS - 2);
                float t = (c - cell * sub) / (float) sub;
                for (int a = 0; a < 3; a++) {
                    across[(r * fc + c) * 3 + a] = spline(coarse(points, cell - 1, r, a),
                            coarse(points, cell, r, a), coarse(points, cell + 1, r, a), coarse(points, cell + 2, r, a), t);
                }
            }
        }
        float[] fine = new float[fc * fr * 3];
        for (int r = 0; r < fr; r++) {
            int cell = Math.min(r / sub, ROWS - 2);
            float t = (r - cell * sub) / (float) sub;
            for (int c = 0; c < fc; c++) {
                for (int a = 0; a < 3; a++) {
                    fine[fine(fc, c, r) * 3 + a] = spline(row(across, fc, c, cell - 1, a), row(across, fc, c, cell, a),
                            row(across, fc, c, cell + 1, a), row(across, fc, c, cell + 2, a), t);
                }
            }
        }
        return fine;
    }

    private static float coarse(float[] points, int column, int row, int axis) {
        if (column < 0) return 2 * points[index(0, row) * 3 + axis] - points[index(1, row) * 3 + axis];
        if (column >= COLUMNS) {
            return 2 * points[index(COLUMNS - 1, row) * 3 + axis] - points[index(COLUMNS - 2, row) * 3 + axis];
        }
        return points[index(column, row) * 3 + axis];
    }

    private static float row(float[] across, int fc, int column, int row, int axis) {
        if (row < 0) return 2 * across[column * 3 + axis] - across[(fc + column) * 3 + axis];
        if (row >= ROWS) {
            return 2 * across[((ROWS - 1) * fc + column) * 3 + axis]
                    - across[((ROWS - 2) * fc + column) * 3 + axis];
        }
        return across[(row * fc + column) * 3 + axis];
    }

    private static float spline(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (2 * p1 + (p2 - p0) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (3 * p1 - p0 - 3 * p2 + p3) * t3);
    }

    private static void quad(VertexConsumer vertices, PoseStack.Pose pose, float[] surface, int[] corners, float[] uvs,
                             float[] normals, float facing, boolean reversed, int light, int overlay) {
        for (int k = 0; k < 4; k++) {
            int n = reversed ? 3 - k : k;
            emit(vertices, pose, surface, corners[n], uvs[n * 2], uvs[n * 2 + 1], normals, facing, light, overlay);
        }
    }

    private static void emit(VertexConsumer vertices, PoseStack.Pose pose, float[] surface, int i, float u, float v,
                             float[] normals, float facing, int light, int overlay) {
        vertices.addVertex(pose, surface[i * 3] / 16, surface[i * 3 + 1] / 16, surface[i * 3 + 2] / 16)
                .setColor(-1).setUv(u / TEXTURE_WIDTH, v / TEXTURE_HEIGHT).setOverlay(overlay).setLight(light)
                .setNormal(pose, normals[i * 3] * facing, normals[i * 3 + 1] * facing, normals[i * 3 + 2] * facing);
    }

    private static void edge(VertexConsumer vertices, PoseStack.Pose pose, float[] outer, float[] inner, int a, int b,
                             float ua, float va, float ub, float vb, int light, int overlay) {
        float ex = outer[b * 3] - outer[a * 3];
        float ey = outer[b * 3 + 1] - outer[a * 3 + 1];
        float ez = outer[b * 3 + 2] - outer[a * 3 + 2];
        float tx = outer[a * 3] - inner[a * 3];
        float ty = outer[a * 3 + 1] - inner[a * 3 + 1];
        float tz = outer[a * 3 + 2] - inner[a * 3 + 2];
        float nx = ey * tz - ez * ty;
        float ny = ez * tx - ex * tz;
        float nz = ex * ty - ey * tx;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-6f) return;
        nx /= length;
        ny /= length;
        nz /= length;
        float[][] corners = {
                {outer[a * 3], outer[a * 3 + 1], outer[a * 3 + 2], ua, va},
                {outer[b * 3], outer[b * 3 + 1], outer[b * 3 + 2], ub, vb},
                {inner[b * 3], inner[b * 3 + 1], inner[b * 3 + 2], ub, vb},
                {inner[a * 3], inner[a * 3 + 1], inner[a * 3 + 2], ua, va}};
        for (int pass = 0; pass < 2; pass++) {
            for (int n = 0; n < 4; n++) {
                var corner = corners[pass == 0 ? n : 3 - n];
                float sign = pass == 0 ? 1 : -1;
                vertices.addVertex(pose, corner[0] / 16, corner[1] / 16, corner[2] / 16).setColor(-1)
                        .setUv(corner[3] / TEXTURE_WIDTH, corner[4] / TEXTURE_HEIGHT).setOverlay(overlay).setLight(light)
                        .setNormal(pose, nx * sign, ny * sign, nz * sign);
            }
        }
    }

    private static final class Part {
        private final Vector3f offset;
        private final Matrix3f rotation;
        private final Matrix3f inverse;
        private final Vector3f center;
        private final Vector3f half;
        private final float radius;
        private final boolean backOnly;
        private final boolean downOnly;

        private Part(ModelPart part, float minX, float maxX, float minY, float maxY, float minZ, float maxZ, float inflate,
                     boolean backOnly) {
            this(part, minX, maxX, minY, maxY, minZ, maxZ, inflate, backOnly, false);
        }

        private Part(ModelPart part, float minX, float maxX, float minY, float maxY, float minZ, float maxZ, float inflate,
                     boolean backOnly, boolean downOnly) {
            offset = new Vector3f(part.x, part.y, part.z);
            rotation = new Matrix3f().rotationZYX(part.zRot, part.yRot, part.xRot);
            inverse = new Matrix3f(rotation).transpose();
            float grow = inflate + HALF;
            center = new Vector3f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
            half = new Vector3f((maxX - minX) / 2 + grow, (maxY - minY) / 2 + inflate, (maxZ - minZ) / 2 + grow);
            radius = Math.min(CORNER_RADIUS, Math.min(half.x, Math.min(half.y, half.z)));
            this.backOnly = backOnly;
            this.downOnly = downOnly;
        }

        private static float exitAlong(float position, float direction, float extent) {
            if (Math.abs(direction) < 1.0e-5f) return Float.MAX_VALUE;
            return ((direction > 0 ? extent : -extent) - position) / direction;
        }

        private boolean push(Vector3f model, Matrix3f bodyRotation) {
            if (downOnly) {
                var local = inverse.transform(new Vector3f(model).sub(offset)).sub(center);
                if (Math.abs(local.x) >= half.x || Math.abs(local.y) >= half.y || Math.abs(local.z) >= half.z) return false;
                var down = bodyRotation.transform(new Vector3f(0, 1, 0));
                var direction = inverse.transform(new Vector3f(down));
                float exit = Float.MAX_VALUE;
                exit = Math.min(exit, exitAlong(local.x, direction.x, half.x));
                exit = Math.min(exit, exitAlong(local.y, direction.y, half.y));
                exit = Math.min(exit, exitAlong(local.z, direction.z, half.z));
                if (exit == Float.MAX_VALUE) return false;
                model.add(down.mul(exit));
                return true;
            }
            var local = inverse.transform(new Vector3f(model).sub(offset)).sub(center);
            if (backOnly) {
                if (Math.abs(local.x) >= half.x || Math.abs(local.y) >= half.y || local.z >= half.z || local.z <= -half.z) return false;
                local.z = half.z;
            } else {
                float qx = Math.abs(local.x) - (half.x - radius);
                float qy = Math.abs(local.y) - (half.y - radius);
                float qz = Math.abs(local.z) - (half.z - radius);
                float ox = Math.max(qx, 0);
                float oy = Math.max(qy, 0);
                float oz = Math.max(qz, 0);
                float outside = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
                float distance = outside + Math.min(Math.max(qx, Math.max(qy, qz)), 0) - radius;
                if (distance >= 0) return false;
                float gx;
                float gy;
                float gz;
                if (outside > 1.0e-6f) {
                    gx = ox / outside;
                    gy = oy / outside;
                    gz = oz / outside;
                } else if (qx >= qy && qx >= qz) {
                    gx = 1;
                    gy = 0;
                    gz = 0;
                } else if (qy >= qz) {
                    gx = 0;
                    gy = 1;
                    gz = 0;
                } else {
                    gx = 0;
                    gy = 0;
                    gz = 1;
                }
                local.x += Math.copySign(gx, local.x) * -distance;
                local.y += Math.copySign(gy, local.y) * -distance;
                local.z += Math.copySign(gz, local.z) * -distance;
            }
            model.set(rotation.transform(local.add(center)).add(offset));
            return true;
        }
    }

    private static final class Body {
        private final Matrix3f rotation;
        private final Matrix3f inverse;
        private final Vector3f bodyOffset;
        private final Vector3f origin;
        private final float anchorZ;
        private final float[] anchors = new float[COLUMNS * 3];
        private final Part[] parts;

        private Body(AvatarRenderState state, ModelPart root, ModelPart body) {
            boolean chest = state.chestEquipment != null && !state.chestEquipment.isEmpty();
            boolean legs = state.legsEquipment != null && !state.legsEquipment.isEmpty();
            boolean feet = state.feetEquipment != null && !state.feetEquipment.isEmpty();
            float torsoArmor = chest ? OUTER_ARMOR : legs ? INNER_ARMOR : 0;
            float legArmor = feet ? OUTER_ARMOR : legs ? INNER_ARMOR : 0;
            float headArmor = state.headEquipment != null && !state.headEquipment.isEmpty() ? OUTER_ARMOR : 0;
            rotation = new Matrix3f().rotationZYX(body.zRot, body.yRot, body.xRot);
            inverse = new Matrix3f(rotation).transpose();
            bodyOffset = new Vector3f(body.x, body.y, body.z);
            origin = new Vector3f(0, chest ? CHEST_SHIFT_Y : 0, 2 + (chest ? CHEST_SHIFT_Z : 0));
            anchorZ = 2 + torsoArmor + HALF - origin.z;
            parts = new Part[] {
                    new Part(root.getChild("head"), -4, 4, -8, 0, -4, 4, headArmor, false, true),
                    new Part(root.getChild("right_leg"), -2, 2, 0, 12, -2, 2, legArmor, false),
                    new Part(root.getChild("left_leg"), -2, 2, 0, 12, -2, 2, legArmor, false),
                    new Part(body, -8, 8, 0, 12, -2, 2, torsoArmor, true)};
            for (int c = 0; c < COLUMNS; c++) {
                var model = toModel(restX(c), 0, anchorZ);
                parts[0].push(model, rotation);
                var local = inverse.transform(model.sub(bodyOffset)).sub(origin);
                anchors[c * 3] = local.x;
                anchors[c * 3 + 1] = local.y;
                anchors[c * 3 + 2] = local.z;
            }
        }

        private Vector3f toCape(Vector3f model) {
            return inverse.transform(new Vector3f(model));
        }

        private boolean collide(float[] p, int i) {
            var model = rotation.transform(new Vector3f(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).add(origin)).add(bodyOffset);
            boolean moved = false;
            for (var part : parts) moved |= part.push(model, rotation);
            if (!moved) return false;
            var local = inverse.transform(model.sub(bodyOffset)).sub(origin);
            p[i * 3] = local.x;
            p[i * 3 + 1] = local.y;
            p[i * 3 + 2] = local.z;
            return true;
        }

        private Vector3f toModel(float x, float y, float z) {
            return rotation.transform(new Vector3f(x, y, z).add(origin)).add(bodyOffset);
        }
    }

    private record Frame(Level level, Vec3 position, Vec3 back, Vec3 left, Body body, float neck) {
        private boolean blocked(float x, float y, float z) {
            if (level == null) return false;
            var model = body.toModel(x, y, z);
            var point = position.add(0, neck - (model.y - body.bodyOffset.y) / PIXELS_PER_BLOCK, 0)
                    .add(back.scale(model.z / PIXELS_PER_BLOCK)).add(left.scale(model.x / PIXELS_PER_BLOCK));
            var pos = BlockPos.containing(point);
            var shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) return false;
            for (var box : shape.toAabbs()) {
                if (box.move(pos).contains(point)) return true;
            }
            return false;
        }
    }

    private static final class Cloth {
        private final float[] p = new float[COUNT * 3];
        private final float[] previous = new float[COUNT * 3];
        private final float[] normals = new float[COUNT * 3];
        private final float[] contact = new float[COUNT * 3];
        private Vec3 position;
        private float yaw;
        private float pending;
        private float time;
        private final Vector3f player = new Vector3f();
        private long seen = System.nanoTime();

        private void reset(Body body) {
            for (int i = 0; i < COUNT; i++) {
                p[i * 3] = previous[i * 3] = restX(i);
                p[i * 3 + 1] = previous[i * 3 + 1] = restY(i);
                p[i * 3 + 2] = previous[i * 3 + 2] = body.anchorZ;
            }
            pending = 0;
            player.zero();
        }

        private float[] snapshot() {
            return p.clone();
        }

        private void step(float delta, Vector3f moved, float turn, Vector3f gravity, Body body, Frame frame) {
            if (delta <= 0) return;
            float blend = Math.min(1, delta * VELOCITY_SMOOTHING);
            var smooth = new Vector3f(moved).div(delta).sub(player).mul(blend).add(player);
            var accel = new Vector3f(smooth).sub(player).div(delta);
            if (accel.length() > MAX_ACCELERATION) accel.normalize(MAX_ACCELERATION);
            player.set(smooth);
            float spin = turn / delta;
            pending += delta;
            while (pending >= STEP) {
                pending -= STEP;
                time += STEP;
                integrate(gravity, accel, spin, body);
                solve(body);
                separate();
            }
            for (int i = COLUMNS; i < COUNT; i++) push(i, frame);
        }

        private void computeNormals() {
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLUMNS; c++) {
                    int i = index(c, r);
                    int left = index(Math.max(0, c - 1), r);
                    int right = index(Math.min(COLUMNS - 1, c + 1), r);
                    int up = index(c, Math.max(0, r - 1));
                    int down = index(c, Math.min(ROWS - 1, r + 1));
                    float ax = p[right * 3] - p[left * 3];
                    float ay = p[right * 3 + 1] - p[left * 3 + 1];
                    float az = p[right * 3 + 2] - p[left * 3 + 2];
                    float bx = p[down * 3] - p[up * 3];
                    float by = p[down * 3 + 1] - p[up * 3 + 1];
                    float bz = p[down * 3 + 2] - p[up * 3 + 2];
                    float nx = ay * bz - az * by;
                    float ny = az * bx - ax * bz;
                    float nz = ax * by - ay * bx;
                    float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (length < 1.0e-6f) {
                        normals[i * 3] = 0;
                        normals[i * 3 + 1] = 0;
                        normals[i * 3 + 2] = 1;
                    } else {
                        normals[i * 3] = nx / length;
                        normals[i * 3 + 1] = ny / length;
                        normals[i * 3 + 2] = nz / length;
                    }
                }
            }
        }

        private void integrate(Vector3f gravity, Vector3f accel, float spin, Body body) {
            computeNormals();
            float dt2 = STEP * STEP;
            for (int i = COLUMNS; i < COUNT; i++) {
                int o = i * 3;
                float vx = (p[o] - previous[o]) * DAMPING;
                float vy = (p[o + 1] - previous[o + 1]) * DAMPING;
                float vz = (p[o + 2] - previous[o + 2]) * DAMPING;
                float airX = vx / STEP + player.x + spin * p[o + 2];
                float airY = vy / STEP + player.y;
                float airZ = vz / STEP + player.z - spin * p[o];
                float nx = normals[o];
                float ny = normals[o + 1];
                float nz = normals[o + 2];
                float normalSpeed = airX * nx + airY * ny + airZ * nz;
                float pressure = -NORMAL_DRAG * normalSpeed * Math.abs(normalSpeed);
                float tangentX = airX - normalSpeed * nx;
                float tangentY = airY - normalSpeed * ny;
                float tangentZ = airZ - normalSpeed * nz;
                float speed = (float) Math.sqrt(airX * airX + airY * airY + airZ * airZ);
                float gust = TURBULENCE * speed * (float) (Math.sin(time * 11.3 + i * 0.37) + Math.sin(time * 17.9 - i * 0.61));
                float fx = pressure * nx - TANGENT_DRAG * tangentX - accel.x;
                float fy = gravity.y + pressure * ny - TANGENT_DRAG * tangentY - accel.y;
                float fz = gravity.z + pressure * nz - TANGENT_DRAG * tangentZ - accel.z + gust;
                previous[o] = p[o];
                previous[o + 1] = p[o + 1];
                previous[o + 2] = p[o + 2];
                p[o] += vx + fx * dt2;
                p[o + 1] += vy + fy * dt2;
                p[o + 2] += vz + fz * dt2;
            }
        }

        private void solve(Body body) {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                for (int c = 0; c < COLUMNS; c++) {
                    int o = c * 3;
                    p[o] = body.anchors[o];
                    p[o + 1] = body.anchors[o + 1];
                    p[o + 2] = body.anchors[o + 2];
                }
                for (int l = 0; l < LINKS.length; l += 3) {
                    int a = LINKS[l];
                    int b = LINKS[l + 1];
                    float stiffness = LINKS[l + 2] == 1 ? BEND_STIFFNESS : LINKS[l + 2] == 2 ? WIDTH_BEND_STIFFNESS : 1;
                    float rest = (float) Math.sqrt(sq(restX(a) - restX(b)) + sq(restY(a) - restY(b)));
                    int oa = a * 3;
                    int ob = b * 3;
                    float dx = p[ob] - p[oa];
                    float dy = p[ob + 1] - p[oa + 1];
                    float dz = p[ob + 2] - p[oa + 2];
                    float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (length < 1.0e-6f) continue;
                    float error = (length - rest) / length * stiffness;
                    boolean pinnedA = a < COLUMNS;
                    boolean pinnedB = b < COLUMNS;
                    float shareA = pinnedA ? 0 : pinnedB ? 1 : 0.5f;
                    float shareB = pinnedB ? 0 : pinnedA ? 1 : 0.5f;
                    p[oa] += dx * error * shareA;
                    p[oa + 1] += dy * error * shareA;
                    p[oa + 2] += dz * error * shareA;
                    p[ob] -= dx * error * shareB;
                    p[ob + 1] -= dy * error * shareB;
                    p[ob + 2] -= dz * error * shareB;
                }
                for (int i = COLUMNS; i < COUNT; i++) {
                    int c = i % COLUMNS;
                    int o = i * 3;
                    float limit = restY(i);
                    int a = c * 3;
                    float dx = p[o] - body.anchors[a];
                    float dy = p[o + 1] - body.anchors[a + 1];
                    float dz = p[o + 2] - body.anchors[a + 2];
                    float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (length > limit) {
                        float scale = limit / length;
                        p[o] = body.anchors[a] + dx * scale;
                        p[o + 1] = body.anchors[a + 1] + dy * scale;
                        p[o + 2] = body.anchors[a + 2] + dz * scale;
                    }
                }
                for (int i = COLUMNS; i < COUNT; i++) {
                    int o = i * 3;
                    float beforeX = p[o];
                    float beforeY = p[o + 1];
                    float beforeZ = p[o + 2];
                    if (body.collide(p, i)) {
                        contact[o] += p[o] - beforeX;
                        contact[o + 1] += p[o + 1] - beforeY;
                        contact[o + 2] += p[o + 2] - beforeZ;
                    }
                }
            }
            for (int o = COLUMNS * 3; o < COUNT * 3; o++) {
                previous[o] += contact[o] * CONTACT_FRICTION;
                contact[o] = 0;
            }
        }

        private void separate() {
            float minimum = THICKNESS * 2;
            float minimumSquared = minimum * minimum;
            for (int a = COLUMNS; a < COUNT; a++) {
                int ca = a % COLUMNS;
                int ra = a / COLUMNS;
                int oa = a * 3;
                for (int b = a + 1; b < COUNT; b++) {
                    int cb = b % COLUMNS;
                    int rb = b / COLUMNS;
                    if (Math.abs(ca - cb) <= 1 && Math.abs(ra - rb) <= 1) continue;
                    int ob = b * 3;
                    float dx = p[ob] - p[oa];
                    float dy = p[ob + 1] - p[oa + 1];
                    float dz = p[ob + 2] - p[oa + 2];
                    float squared = dx * dx + dy * dy + dz * dz;
                    if (squared >= minimumSquared || squared < 1.0e-10f) continue;
                    float length = (float) Math.sqrt(squared);
                    float correction = (minimum - length) / length / 2;
                    p[oa] -= dx * correction;
                    p[oa + 1] -= dy * correction;
                    p[oa + 2] -= dz * correction;
                    p[ob] += dx * correction;
                    p[ob + 1] += dy * correction;
                    p[ob + 2] += dz * correction;
                }
            }
        }

        private void push(int i, Frame frame) {
            int o = i * 3;
            if (!frame.blocked(p[o], p[o + 1], p[o + 2])) return;
            for (int n = 1; n <= PUSH_STEPS; n++) {
                float distance = n * PUSH_STEP;
                if (!frame.blocked(p[o], p[o + 1] - distance, p[o + 2])) {
                    p[o + 1] -= distance;
                    settle(o);
                    return;
                }
                if (!frame.blocked(p[o], p[o + 1], p[o + 2] - distance)) {
                    p[o + 2] -= distance;
                    settle(o);
                    return;
                }
            }
        }

        private void settle(int o) {
            previous[o] = p[o];
            previous[o + 1] = p[o + 1];
            previous[o + 2] = p[o + 2];
        }
    }

    private static float sq(float value) {
        return value * value;
    }
}
