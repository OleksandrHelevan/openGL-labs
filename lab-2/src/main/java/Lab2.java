import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.util.Objects;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Lab2 {

    private long window;
    private float cubeRotation = 0.2f;        // обертання всього кубика
    private float topLayerRotation = 1.0f;    // обертання верхнього шару
    private float scale = 1.0f;
    private boolean isPaused = false;


    private RotationPhase phase = RotationPhase.VERTICAL;
    private float currentRotation = 0.0f;   // поточний кут обертання

    private float cubeAngle = 0.0f;

    // --- метод для створення матриці обертання навколо Y ---
    private float[] createRotationMatrixY(float angle) {
        float rad = (float) Math.toRadians(angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        return new float[]{
                cos, 0, -sin, 0,
                0,   1,  0,   0,
                sin, 0,  cos, 0,
                0,   0,  0,   1
        };
    }

    // --- метод для створення матриці переносу ---
    private float[] createTranslationMatrix(float x, float y) {
        return new float[]{
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                x, y, (float) -10.0, 1
        };
    }

    // --- метод для створення матриці масштабування ---
    private float[] createScaleMatrix(float s) {
        return new float[]{
                s, 0, 0, 0,
                0, s, 0, 0,
                0, 0, s, 0,
                0, 0, 0, 1
        };
    }


    private final MiniCube[][][] cubes = new MiniCube[3][3][3];

    public void run() {
        initCubes();
        init();
        loop();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }

    private void initCubes() {
        float gap = 0.02f; // ширина чорної щілини
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                for (int z = 0; z < 3; z++)
                    cubes[x][y][z] = new MiniCube(x - 1, y - 1, z - 1, gap);
    }

    private void init() {

        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        window = glfwCreateWindow(800, 600, "Realistic Rubik's Cube Lab2", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_CULL_FACE);
        glEnable(GL_COLOR_MATERIAL);

        glClearColor(0.0f, 0.0f, 0.0f, 1.0f); // чорний фон для щілин

        glfwSetScrollCallback(window, (win, xoffset, yoffset) -> {
            if (yoffset > 0) scale *= 1.1f;
            else scale /= 1.1f;
        });
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_SPACE && action == GLFW_PRESS) {
                isPaused = !isPaused;
            }
        });
    }

    private void loop() {
        setPerspective();

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glLoadIdentity();

            // будуємо власні матриці трансформацій
            float translateX = 0.0f;
            float translateY = 0.0f;
            float[] translateMatrix = createTranslationMatrix(translateX, translateY);
            float[] scaleMatrix = createScaleMatrix(scale);
            float[] rotateMatrix = createRotationMatrixY(cubeAngle);

            // застосовуємо їх замість glTranslate/glScale/glRotate
            glMultMatrixf(translateMatrix);
            glMultMatrixf(scaleMatrix);
            glMultMatrixf(rotateMatrix);

            // трохи нахил камери залишимо як було
            glRotatef(30, 1, 0, 0);
            glRotatef(-45, 0, 1, 0);

            drawRubik();

            // оновлюємо кут обертання тільки якщо не на паузі
            if (!isPaused) {
                cubeAngle += 0.5f;
            }


            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }


    private void drawRubik() {
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    glPushMatrix();

                    // Послідовне обертання шару
                    if (phase == RotationPhase.VERTICAL && z == 2) {
                        glTranslatef(0, 0, 1);          // центр обертання
                        glRotatef(currentRotation, 0, 0, 1); // обертання навколо Z
                        glTranslatef(0, 0, -1);
                    } else if (phase == RotationPhase.HORIZONTAL && y == 2) {
                        glTranslatef(0, 1, 0);          // центр обертання
                        glRotatef(currentRotation, 0, 1, 0); // обертання навколо Y
                        glTranslatef(0, -1, 0);
                    }

                    cubes[x][y][z].draw();
                    glPopMatrix();
                }
            }
        }

        // Оновлюємо кут обертання
        float rotationSpeed = 1.0f;
        currentRotation += rotationSpeed;

        // Після завершення обертання 90°
        // Після завершення обертання 90° - більше нічого робити
        if (currentRotation >= 90.0f) {
            currentRotation = 0.0f;
            phase = (phase == RotationPhase.VERTICAL) ? RotationPhase.HORIZONTAL : RotationPhase.VERTICAL;
        }

    }

    private void setPerspective() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float fH = (float) Math.tan(Math.toRadians((float) 45.0 / 2)) * (float) 0.1;
        float fW = fH * (float) 1.3333334;
        glFrustum(-fW, fW, -fH, fH, (float) 0.1, (float) 100.0);
        glMatrixMode(GL_MODELVIEW);
    }

    public static void main(String[] args) {
        new Lab2().run();
    }

    public float getTopLayerRotation() {
        return topLayerRotation;
    }

    public void setTopLayerRotation(float topLayerRotation) {
        this.topLayerRotation = topLayerRotation;
    }

    public float getCubeRotation() {
        return cubeRotation;
    }

    public void setCubeRotation(float cubeRotation) {
        this.cubeRotation = cubeRotation;
    }
}
