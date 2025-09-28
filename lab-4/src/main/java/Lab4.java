import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Lab4 {

    private long window;

    private float light0X = -2.0f, light0Y = 2.0f, light0Z = 2.0f;
    private boolean light1Enabled = true;
    private boolean light2Enabled = true;

    private float rotX = 0.0f, rotY = 0.0f;

    private float[] ambient0 = {0.1f, 0.1f, 0.2f, 1.0f};
    private float[] diffuse0 = {0.2f, 0.2f, 1.0f, 1.0f};
    private float[] specular0 = {0.5f, 0.5f, 1.0f, 1.0f};

    private float[] ambient1 = {0.1f, 0.2f, 0.1f, 1.0f};
    private float[] diffuse1 = {0.2f, 1.0f, 0.2f, 1.0f};
    private float[] specular1 = {0.5f, 1.0f, 0.5f, 1.0f};

    private float[] ambient2 = {0.2f, 0.05f, 0.05f, 1.0f};
    private float[] diffuse2 = {1.0f, 0.2f, 0.2f, 1.0f};
    private float[] specular2 = {1.0f, 0.5f, 0.5f, 1.0f};

    private float[] matAmbient = {0.6f, 0.6f, 0.6f, 1.0f};
    private float[] matDiffuse = {0.6f, 0.6f, 0.6f, 1.0f};
    private float[] matSpecular = {1.0f, 1.0f, 1.0f, 1.0f};
    private float matShininess = 50.0f;

    public static void main(String[] args) {
        new Lab4().run();
    }

    private FloatBuffer asFloatBuffer(float... values) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(values.length);
        buffer.put(values).flip();
        return buffer;
    }

    public void run() {
        init();
        loop();
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(800, 600, "Cube with Three Lights", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);

        glEnable(GL_LIGHT0);
        glEnable(GL_LIGHT1);
        glEnable(GL_LIGHT2);

        updateLights();
        updateMaterial();
    }

    private void updateLights() {
        glLightfv(GL_LIGHT0, GL_POSITION, asFloatBuffer(light0X, light0Y, light0Z, 1.0f));
        glLightfv(GL_LIGHT0, GL_AMBIENT, asFloatBuffer(ambient0));
        glLightfv(GL_LIGHT0, GL_DIFFUSE, asFloatBuffer(diffuse0));
        glLightfv(GL_LIGHT0, GL_SPECULAR, asFloatBuffer(specular0));

        if (light1Enabled) glEnable(GL_LIGHT1);
        else glDisable(GL_LIGHT1);

        float light1X = 2.0f, light1Y = 2.0f, light1Z = 2.0f;
        glLightfv(GL_LIGHT1, GL_POSITION, asFloatBuffer(light1X, light1Y, light1Z, 1.0f));
        glLightfv(GL_LIGHT1, GL_AMBIENT, asFloatBuffer(ambient1));
        glLightfv(GL_LIGHT1, GL_DIFFUSE, asFloatBuffer(diffuse1));
        glLightfv(GL_LIGHT1, GL_SPECULAR, asFloatBuffer(specular1));
        glLightf(GL_LIGHT1, GL_SPOT_CUTOFF, 45f);
        glLightfv(GL_LIGHT1, GL_SPOT_DIRECTION, asFloatBuffer(-1f, -1f, -1f, 0f));
        glLightf(GL_LIGHT1, GL_CONSTANT_ATTENUATION, 1f);
        glLightf(GL_LIGHT1, GL_LINEAR_ATTENUATION, 0.1f);
        glLightf(GL_LIGHT1, GL_QUADRATIC_ATTENUATION, 0.05f);

        if (light2Enabled) glEnable(GL_LIGHT2);
        else glDisable(GL_LIGHT2);

        glLightfv(GL_LIGHT2, GL_POSITION, asFloatBuffer(0f, -1f, 0f, 0f)); // w=0 → directional
        glLightfv(GL_LIGHT2, GL_AMBIENT, asFloatBuffer(ambient2));
        glLightfv(GL_LIGHT2, GL_DIFFUSE, asFloatBuffer(diffuse2));
        glLightfv(GL_LIGHT2, GL_SPECULAR, asFloatBuffer(specular2));
    }

    private void updateMaterial() {
        glMaterialfv(GL_FRONT, GL_AMBIENT, asFloatBuffer(matAmbient));
        glMaterialfv(GL_FRONT, GL_DIFFUSE, asFloatBuffer(matDiffuse));
        glMaterialfv(GL_FRONT, GL_SPECULAR, asFloatBuffer(matSpecular));
        glMaterialf(GL_FRONT, GL_SHININESS, matShininess);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            processInput();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glFrustum(-1, 1, -1, 1, 1, 10);

            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();
            glTranslatef(0, 0, -5);
            glRotatef(rotX, 1, 0, 0);
            glRotatef(rotY, 0, 1, 0);

            updateLights();
            updateMaterial();
            drawCube();

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void processInput() {
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) rotX -= 1.5f;
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) rotX += 1.5f;
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) rotY -= 1.5f;
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) rotY += 1.5f;

        if (glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS) light0Y += 0.1f;
        if (glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS) light0Y -= 0.1f;
        if (glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS) light0X -= 0.1f;
        if (glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS) light0X += 0.1f;
        if (glfwGetKey(window, GLFW_KEY_Z) == GLFW_PRESS) light0Z += 0.1f;
        if (glfwGetKey(window, GLFW_KEY_X) == GLFW_PRESS) light0Z -= 0.1f;

        if (glfwGetKey(window, GLFW_KEY_1) == GLFW_PRESS) light1Enabled = true;
        if (glfwGetKey(window, GLFW_KEY_2) == GLFW_PRESS) light1Enabled = false;

        if (glfwGetKey(window, GLFW_KEY_3) == GLFW_PRESS) light2Enabled = true;
        if (glfwGetKey(window, GLFW_KEY_4) == GLFW_PRESS) light2Enabled = false;
    }

    private void drawCube() {
        glBegin(GL_QUADS);

        glNormal3f(0, 0, 1);
        glVertex3f(-1, -1, 1);
        glVertex3f(1, -1, 1);
        glVertex3f(1, 1, 1);
        glVertex3f(-1, 1, 1);

        glNormal3f(0, 0, -1);
        glVertex3f(-1, -1, -1);
        glVertex3f(-1, 1, -1);
        glVertex3f(1, 1, -1);
        glVertex3f(1, -1, -1);

        glNormal3f(-1, 0, 0);
        glVertex3f(-1, -1, -1);
        glVertex3f(-1, -1, 1);
        glVertex3f(-1, 1, 1);
        glVertex3f(-1, 1, -1);

        glNormal3f(1, 0, 0);
        glVertex3f(1, -1, -1);
        glVertex3f(1, 1, -1);
        glVertex3f(1, 1, 1);
        glVertex3f(1, -1, 1);

        glNormal3f(0, 1, 0);
        glVertex3f(-1, 1, -1);
        glVertex3f(-1, 1, 1);
        glVertex3f(1, 1, 1);
        glVertex3f(1, 1, -1);

        glNormal3f(0, -1, 0);
        glVertex3f(-1, -1, -1);
        glVertex3f(1, -1, -1);
        glVertex3f(1, -1, 1);
        glVertex3f(-1, -1, 1);

        glEnd();
    }
}
