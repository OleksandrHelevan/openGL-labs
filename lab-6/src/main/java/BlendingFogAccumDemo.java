import org.lwjgl.*;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13C.GL_MULTISAMPLE;
import static org.lwjgl.system.MemoryUtil.NULL;

/*
B — вкл/викл blending; [/ ] — цикл по Src/Dst факторах.
T — вкл/викл alpha-test; - / = — поріг glAlphaFunc.
F — цикл режимів туману: LINEAR → EXP → EXP2.
L — вкл/викл GL_LOGIC_OP; ; — цикл логічних операцій.
M — вкл/викл motion blur через glAccum (EMA 0.9/0.1).
C — вкл/викл glCullFace; D — керувати glDepthMask для прозорих.
R — очистити буфер-накопичувач.
ESC — вихід.
 */

public class BlendingFogAccumDemo {

    private long window;

    private boolean useBlend = true;
    private boolean useAlphaTest = true;
    private boolean useLogicOp = false;
    private boolean useCull = true;
    private boolean depthMaskForTranslucent = false;
    private boolean motionBlur = false;

    private float alphaRef = 0.25f;

    private final int[] SRC_FACTORS = {
            GL_SRC_ALPHA, GL_ONE, GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA, GL_SRC_COLOR, GL_ONE_MINUS_SRC_ALPHA
    };
    private final int[] DST_FACTORS = {
            GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_DST_ALPHA, GL_DST_ALPHA, GL_ONE_MINUS_SRC_COLOR, GL_SRC_ALPHA
    };
    private int srcIdx = 0, dstIdx = 0;

    private final int[] LOGIC_OPS = {
            GL_COPY, GL_XOR, GL_OR, GL_AND, GL_EQUIV, GL_INVERT, GL_OR_REVERSE, GL_AND_REVERSE
    };
    private int logicIdx = 0;

    private final int[] FOG_MODES = {GL_LINEAR, GL_EXP, GL_EXP2};
    private int fogIdx = 0;

    private final int width = 1280;
    private final int height = 720;

    public static void main(String[] args) {
        new BlendingFogAccumDemo().run();
    }

    public void run() {
        initWindow();
        initGL();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void initWindow() {
        if (!glfwInit()) throw new IllegalStateException("GLFW init failed");

        glfwDefaultWindowHints();

        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);

        glfwWindowHint(GLFW_ACCUM_RED_BITS, 16);
        glfwWindowHint(GLFW_ACCUM_GREEN_BITS, 16);
        glfwWindowHint(GLFW_ACCUM_BLUE_BITS, 16);
        glfwWindowHint(GLFW_ACCUM_ALPHA_BITS, 16);

        glfwWindowHint(GLFW_SAMPLES, 4);

        window = glfwCreateWindow(width, height, "LWJGL", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        glfwSetKeyCallback(window, this::onKey);
    }

    private void initGL() {
        GL.createCapabilities();

        glViewport(0, 0, width, height);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);
        glClearDepth(1.0f);
        glEnable(GL_MULTISAMPLE);
        glClearColor(0.12f, 0.13f, 0.15f, 1.0f);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective();

        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glDisable(GL_LIGHTING);

        glEnable(GL_ALPHA_TEST);
        glAlphaFunc(GL_GREATER, alphaRef);

        glEnable(GL_BLEND);
        glBlendFunc(SRC_FACTORS[srcIdx], DST_FACTORS[dstIdx]);

        setupFog();

        glClearAccum(0f, 0f, 0f, 0f);
        glClear(GL_ACCUM_BUFFER_BIT);
    }

    private void setupFog() {
        FloatBuffer fogColor = BufferUtils.createFloatBuffer(4).put(new float[]{0.7f, 0.75f, 0.8f, 1.0f});
        fogColor.flip();

        glFogi(GL_FOG_MODE, FOG_MODES[fogIdx]);
        glFogfv(GL_FOG_COLOR, fogColor);
        glFogf(GL_FOG_DENSITY, 0.12f);
        glFogf(GL_FOG_START, 5f);
        glFogf(GL_FOG_END, 40f);
        boolean useFog = true;
        glEnable(GL_FOG);
    }

    private void loop() {
        double t0 = glfwGetTime();

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            double t = glfwGetTime() - t0;
            float camR = 18f;
            float camX = (float) (Math.cos(t * 0.3) * camR);
            float camZ = (float) (Math.sin(t * 0.3) * camR);
            gluLookAt(camX, camZ);

            if (useAlphaTest) {
                glEnable(GL_ALPHA_TEST);
                glAlphaFunc(GL_GREATER, alphaRef);
            } else glDisable(GL_ALPHA_TEST);

            if (useBlend) {
                glEnable(GL_BLEND);
                glBlendFunc(SRC_FACTORS[srcIdx], DST_FACTORS[dstIdx]);
            } else glDisable(GL_BLEND);

            if (useLogicOp) {
                glEnable(GL_COLOR_LOGIC_OP);
                glLogicOp(LOGIC_OPS[logicIdx]);
            } else glDisable(GL_COLOR_LOGIC_OP);

            if (useCull) {
                glEnable(GL_CULL_FACE);
                glCullFace(GL_BACK);
            } else glDisable(GL_CULL_FACE);

            renderOpaqueScene(t);

            glDepthMask(depthMaskForTranslucent);
            renderTranslucentObjects(t);
            glDepthMask(true);

            if (motionBlur) {
                glAccum(GL_MULT, 0.9f);
                glAccum(GL_ACCUM, 0.1f);
                glAccum(GL_RETURN, 1.0f);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void renderOpaqueScene(double t) {
        glPushMatrix();
        glTranslatef(0, 0, 0);
        drawCheckerFloor();
        glPopMatrix();

        glPushMatrix();
        glTranslatef(-5, 1.5f, -5);
        glRotatef((float) (t * 30 % 360), 0, 1, 0);
        drawSolidCube(3f, 0.7f, 0.2f, 0.2f);
        glPopMatrix();

        glPushMatrix();
        glTranslatef(6, 2.5f, 4);
        glRotatef((float) (t * 50 % 360), 1, 1, 0);
        drawSolidCube(2.5f, 0.2f, 0.6f, 0.3f);
        glPopMatrix();
    }

    private void renderTranslucentObjects(double t) {
        glPushMatrix();
        glTranslatef(0, 3.5f, -2);
        glRotatef(90, 1, 0, 0);
        drawAlphaQuad(0.2f, 0.9f, 0.35f);
        glPopMatrix();

        glPushMatrix();
        glTranslatef(0, 4.0f, 0);
        glRotatef(90, 1, 0, 0);
        drawAlphaQuad(0.9f, 0.2f, 0.4f);
        glPopMatrix();

        glPushMatrix();
        glTranslatef(0, 4.5f, 2.0f);
        glRotatef(90, 1, 0, 0);
        drawMaskedAlphaQuad();
        glPopMatrix();

        glPushMatrix();
        glTranslatef((float) Math.sin(t) * 4f, 2.5f, (float) Math.cos(t * 0.7) * 4f);
        drawAlphaBall();
        glPopMatrix();
    }

    private void drawCheckerFloor() {
        float halfX = 40 * (float) 1.0 * 0.5f;
        float halfZ = 40 * (float) 1.0 * 0.5f;
        glBegin(GL_QUADS);
        for (int x = 0; x < 40; x++) {
            for (int z = 0; z < 40; z++) {
                boolean dark = ((x + z) & 1) == 0;
                glColor4f(dark ? 0.28f : 0.22f, dark ? 0.3f : 0.25f, dark ? 0.34f : 0.28f, 1f);
                float x0 = -halfX + x * (float) 1.0;
                float z0 = -halfZ + z * (float) 1.0;
                glVertex3f(x0, 0, z0);
                glVertex3f(x0 + (float) 1.0, 0, z0);
                glVertex3f(x0 + (float) 1.0, 0, z0 + (float) 1.0);
                glVertex3f(x0, 0, z0 + (float) 1.0);
            }
        }
        glEnd();
    }

    private void drawSolidCube(float s, float r, float g, float b) {
        float h = s * 0.5f;
        glColor4f(r, g, b, (float) 1.0);
        glBegin(GL_QUADS);
        glVertex3f(+h, -h, -h);
        glVertex3f(+h, -h, +h);
        glVertex3f(+h, +h, +h);
        glVertex3f(+h, +h, -h);
        glVertex3f(-h, -h, +h);
        glVertex3f(-h, -h, -h);
        glVertex3f(-h, +h, -h);
        glVertex3f(-h, +h, +h);
        glVertex3f(-h, +h, -h);
        glVertex3f(+h, +h, -h);
        glVertex3f(+h, +h, +h);
        glVertex3f(-h, +h, +h);
        glVertex3f(-h, -h, +h);
        glVertex3f(+h, -h, +h);
        glVertex3f(+h, -h, -h);
        glVertex3f(-h, -h, -h);
        glVertex3f(-h, -h, +h);
        glVertex3f(-h, +h, +h);
        glVertex3f(+h, +h, +h);
        glVertex3f(+h, -h, +h);
        glVertex3f(+h, -h, -h);
        glVertex3f(+h, +h, -h);
        glVertex3f(-h, +h, -h);
        glVertex3f(-h, -h, -h);
        glEnd();
    }

    private void drawAlphaQuad(float r, float b, float a) {
        float hw = (float) 6.0 * 0.5f, hh = (float) 6.0 * 0.5f;
        glColor4f(r, (float) 0.6, b, a);
        glBegin(GL_QUADS);
        glVertex3f(-hw, 0, -hh);
        glVertex3f(+hw, 0, -hh);
        glVertex3f(+hw, 0, +hh);
        glVertex3f(-hw, 0, +hh);
        glEnd();
    }

    private void drawMaskedAlphaQuad() {
        float hw = (float) 6.0 * 0.5f, hh = (float) 6.0 * 0.5f;
        int grid = 40;
        glBegin(GL_QUADS);
        for (int i = 0; i < grid; i++) {
            for (int j = 0; j < grid; j++) {
                float x0 = -hw + (float) 6.0 * i / grid;
                float x1 = -hw + (float) 6.0 * (i + 1) / grid;
                float z0 = -hh + (float) 6.0 * j / grid;
                float z1 = -hh + (float) 6.0 * (j + 1) / grid;

                float cx = (x0 + x1) * 0.5f / hw;
                float cz = (z0 + z1) * 0.5f / hh;
                float dist = (float) Math.sqrt(cx * cx + cz * cz);
                float alpha = 0.7f * Math.max(0f, 1f - dist);

                glColor4f(0.9f, 0.4f, 0.7f, alpha);
                glVertex3f(x0, 0, z0);
                glVertex3f(x1, 0, z0);
                glVertex3f(x1, 0, z1);
                glVertex3f(x0, 0, z1);
            }
        }
        glEnd();
    }

    private void drawAlphaBall() {
        glColor4f((float) 0.5, (float) 0.8, (float) 0.5, (float) 0.35);
        int latBands = 24, lonBands = 32;
        for (int lat = 0; lat < latBands; lat++) {
            float theta1 = (float) (Math.PI * lat / latBands);
            float theta2 = (float) (Math.PI * (lat + 1) / latBands);
            glBegin(GL_TRIANGLE_STRIP);
            for (int lon = 0; lon <= lonBands; lon++) {
                float phi = (float) (2 * Math.PI * lon / lonBands);
                float x1 = (float) (Math.cos(phi) * Math.sin(theta1));
                float y1 = (float) Math.cos(theta1);
                float z1 = (float) (Math.sin(phi) * Math.sin(theta1));
                float x2 = (float) (Math.cos(phi) * Math.sin(theta2));
                float y2 = (float) Math.cos(theta2);
                float z2 = (float) (Math.sin(phi) * Math.sin(theta2));
                glVertex3f((float) 1.8 * x2, (float) 1.8 * y2, (float) 1.8 * z2);
                glVertex3f((float) 1.8 * x1, (float) 1.8 * y1, (float) 1.8 * z1);
            }
            glEnd();
        }
    }

    private void onKey(long win, int key, int scancode, int action, int mods) {
        if (action != GLFW_PRESS) return;
        switch (key) {
            case GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(win, true);
            case GLFW_KEY_B -> useBlend = !useBlend;
            case GLFW_KEY_LEFT_BRACKET -> {
                srcIdx = (srcIdx + 1) % SRC_FACTORS.length;
                glBlendFunc(SRC_FACTORS[srcIdx], DST_FACTORS[dstIdx]);
            }
            case GLFW_KEY_RIGHT_BRACKET -> {
                dstIdx = (dstIdx + 1) % DST_FACTORS.length;
                glBlendFunc(SRC_FACTORS[srcIdx], DST_FACTORS[dstIdx]);
            }
            case GLFW_KEY_T -> useAlphaTest = !useAlphaTest;
            case GLFW_KEY_MINUS -> alphaRef = clamp(alphaRef - 0.05f);
            case GLFW_KEY_EQUAL -> alphaRef = clamp(alphaRef + 0.05f);
            case GLFW_KEY_F -> {
                fogIdx = (fogIdx + 1) % FOG_MODES.length;
                setupFog();
            }
            case GLFW_KEY_L -> useLogicOp = !useLogicOp;
            case GLFW_KEY_SEMICOLON -> logicIdx = (logicIdx + 1) % LOGIC_OPS.length;
            case GLFW_KEY_C -> useCull = !useCull;
            case GLFW_KEY_D -> depthMaskForTranslucent = !depthMaskForTranslucent;
            case GLFW_KEY_M -> motionBlur = !motionBlur;
            case GLFW_KEY_R -> {
                glClearAccum(0f, 0f, 0f, 0f);
                glClear(GL_ACCUM_BUFFER_BIT);
            }
        }
    }

    private static float clamp(float v) {
        return Math.max((float) 0.0, Math.min((float) 1.0, v));
    }

    private static void gluPerspective() {
        float fH = (float) Math.tan((float) 60.0 / 360 * Math.PI) * (float) 0.1;
        float fW = fH * (float) 1.7777778;
        glFrustum(-fW, fW, -fH, fH, (float) 0.1, (float) 100.0);
    }

    private static void gluLookAt(float eyeX, float eyeZ) {
        float[] f = normalize(new float[]{(float) 0 - eyeX, (float) 3.5 - (float) 8.0, (float) 0 - eyeZ});
        float[] up = normalize(new float[]{(float) 0, (float) 1, (float) 0});
        float[] s = cross(f, up);
        float[] u = cross(s, f);

        float[] M = {
                s[0], u[0], -f[0], 0,
                s[1], u[1], -f[1], 0,
                s[2], u[2], -f[2], 0,
                0, 0, 0, 1
        };

        glMultMatrixf(M);
        glTranslatef(-eyeX, -(float) 8.0, -eyeZ);
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]
        };
    }

    private static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new float[]{v[0] / len, v[1] / len, v[2] / len};
    }
}
