import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.util.Objects;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Lab3 {

    private long window;
    private float eyeX = 0, eyeY = 0, eyeZ = 5;
    private boolean perspective = true;
    private boolean pPressedLastFrame = false;

    public void run() {
        init();
        loop();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        Objects.requireNonNull(glfwSetErrorCallback(null)).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(800, 600, "LWJGL3 Full Lab Demo", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glClearColor(0.5f, 0.5f, 0.5f, 1f);

        setProjection(800, 600);

        glfwSetFramebufferSizeCallback(window, (win, width, height) -> {
            glViewport(0, 0, width, height);
            setProjection(width, height);
        });
    }

    private void setProjection(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) width / height;

        if (perspective) {
            float fov = 60.0f;
            float near = 0.1f, far = 100f;
            float y_scale = (float) (1f / Math.tan(Math.toRadians(fov / 2f)));
            float x_scale = y_scale / aspect;
            float frustum_length = far - near;

            FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
            matrix.put(new float[]{
                    x_scale, 0, 0, 0,
                    0, y_scale, 0, 0,
                    0, 0, -((far + near) / frustum_length), -1,
                    0, 0, -((2 * near * far) / frustum_length), 0
            }).flip();
            glLoadMatrixf(matrix);
        } else {
            float orthoSize = 5f;
            glOrtho(-orthoSize * aspect, orthoSize * aspect, -orthoSize, orthoSize, 0.1, 100);
        }

        glMatrixMode(GL_MODELVIEW);
    }

    private void lookAt(float eyeX, float eyeY, float eyeZ,
                        float centerX, float centerY, float centerZ) {

        float[] f = {centerX - eyeX, centerY - eyeY, centerZ - eyeZ};
        float f_mag = (float) Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        f[0] /= f_mag;
        f[1] /= f_mag;
        f[2] /= f_mag;

        float[] up = {(float) 0, (float) 1, (float) 0};
        float up_mag = (float) Math.sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2]);
        up[0] /= up_mag;
        up[1] /= up_mag;
        up[2] /= up_mag;

        float[] s = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        float s_mag = (float) Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        s[0] /= s_mag;
        s[1] /= s_mag;
        s[2] /= s_mag;

        float[] u = {s[1] * f[2] - s[2] * f[1], s[2] * f[0] - s[0] * f[2], s[0] * f[1] - s[1] * f[0]};

        FloatBuffer m = BufferUtils.createFloatBuffer(16);
        m.put(new float[]{
                s[0], u[0], -f[0], 0,
                s[1], u[1], -f[1], 0,
                s[2], u[2], -f[2], 0,
                0, 0, 0, 1
        }).flip();

        glLoadMatrixf(m);
        glTranslatef(-eyeX, -eyeY, -eyeZ);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glLoadIdentity();
            float centerX = 0;
            float centerY = 0;
            float centerZ = 0;
            lookAt(eyeX, eyeY, eyeZ, centerX, centerY, centerZ);

            drawRoomCube();
            glfwSwapBuffers(window);
            glfwPollEvents();
            processInput();
        }
    }

    private void processInput() {
        float speed = 0.1f;
        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) eyeZ -= speed;
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) eyeZ += speed;
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) eyeX -= speed;
        if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) eyeX += speed;
        if (glfwGetKey(window, GLFW_KEY_Z) == GLFW_PRESS) eyeY += speed;
        if (glfwGetKey(window, GLFW_KEY_X) == GLFW_PRESS) eyeY -= speed;

        boolean pCurrently = glfwGetKey(window, GLFW_KEY_P) == GLFW_PRESS;
        if (pCurrently && !pPressedLastFrame) {
            perspective = !perspective;
            int[] width = new int[1], height = new int[1];
            glfwGetFramebufferSize(window, width, height);
            setProjection(width[0], height[0]);
        }
        pPressedLastFrame = pCurrently;
    }


    private void drawRoomCube() {
        glBegin(GL_QUADS);
        glColor3f(1f, 0f, 0f);
        glVertex3f(-5, -5, 5);
        glVertex3f(5, -5, 5);
        glVertex3f(5, 5, 5);
        glVertex3f(-5, 5, 5);
        glColor3f(0f, 1f, 0f);
        glVertex3f(-5, -5, -5);
        glVertex3f(-5, 5, -5);
        glVertex3f(5, 5, -5);
        glVertex3f(5, -5, -5);
        glColor3f(0f, 0f, 1f);
        glVertex3f(-5, -5, -5);
        glVertex3f(-5, -5, 5);
        glVertex3f(-5, 5, 5);
        glVertex3f(-5, 5, -5);
        glColor3f(1f, 1f, 0f);
        glVertex3f(5, -5, -5);
        glVertex3f(5, 5, -5);
        glVertex3f(5, 5, 5);
        glVertex3f(5, -5, 5);
        glColor3f(0f, 1f, 1f);
        glVertex3f(-5, 5, -5);
        glVertex3f(-5, 5, 5);
        glVertex3f(5, 5, 5);
        glVertex3f(5, 5, -5);
        glColor3f(1f, 0f, 1f);
        glVertex3f(-5, -5, -5);
        glVertex3f(5, -5, -5);
        glVertex3f(5, -5, 5);
        glVertex3f(-5, -5, 5);

        glEnd();
    }

    public static void main(String[] args) {
        new Lab3().run();
    }
}
