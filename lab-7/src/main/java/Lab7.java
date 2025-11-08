import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.opengl.GL11.*;

public class Lab7 {

    private long window;
    private int width = 1200;
    private int height = 800;

    // Параметри площини відсікання
    private double[] clipPlane = {1.0, 0.0, 0.0, 0.0};
    private boolean useClipPlane = false;

    // Параметри світла / тіні / дзеркала
    private float[] lightPos = {5.0f, 5.0f, 5.0f, 1.0f};
    private float[] floorPlane = {0.0f, 1.0f, 0.0f, 0.0f};
    private float[] mirrorPlane = {0.0f, 0.0f, 1.0f, 3.0f};

    // Камера
    private float cameraAngleX = 30.0f;
    private float cameraAngleY = 45.0f;
    private float cameraDistance = 15.0f;

    // Миша
    private double mouseX, mouseY;
    private boolean mousePressed = false;
    private double lastMouseX, lastMouseY;

    // Режими відображення
    private int displayMode = 0; // 0-normal, 1-clip, 2-stencil, 3-shadow, 4-mirror
    private boolean showStencilPattern = false;

    // Об’єкт
    private float objectRotation = 0.0f;
    private boolean spinning = true;

    public static void main(String[] args) {
        new Lab7().run();
    }

    public void run() {
        init();
        loop();

        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_STENCIL_BITS, 8);

        window = glfwCreateWindow(width, height, "Lab 7 - Clipping & Stencil Buffer", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create GLFW window");

        setupCallbacks();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);
            GLFWVidMode vid = glfwGetVideoMode(glfwGetPrimaryMonitor());
            glfwSetWindowPos(window, (vid.width() - pWidth.get(0)) / 2, (vid.height() - pHeight.get(0)) / 2);
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_LIGHTING);
        glEnable(GL_LIGHT0);
        glEnable(GL_COLOR_MATERIAL);
        glColorMaterial(GL_FRONT_AND_BACK, GL_AMBIENT_AND_DIFFUSE);
        glEnable(GL_NORMALIZE);
        setupLighting();
    }

    private void setupCallbacks() {
        glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true);
            if (action == GLFW_PRESS) {
                switch (key) {
                    case GLFW_KEY_1 -> displayMode = 0;
                    case GLFW_KEY_2 -> { displayMode = 1; useClipPlane = !useClipPlane; }
                    case GLFW_KEY_3 -> displayMode = 2;
                    case GLFW_KEY_4 -> displayMode = 3;
                    case GLFW_KEY_5 -> displayMode = 4;
                    case GLFW_KEY_SPACE -> spinning = !spinning;
                    case GLFW_KEY_S -> showStencilPattern = !showStencilPattern;
                    case GLFW_KEY_LEFT -> clipPlane[0] -= 0.1;
                    case GLFW_KEY_RIGHT -> clipPlane[0] += 0.1;
                    case GLFW_KEY_UP -> clipPlane[3] += 0.5;
                    case GLFW_KEY_DOWN -> clipPlane[3] -= 0.5;
                    case GLFW_KEY_W -> lightPos[2] -= 0.5f;
                    case GLFW_KEY_A -> lightPos[0] -= 0.5f;
                    case GLFW_KEY_D -> lightPos[0] += 0.5f;
                    case GLFW_KEY_Q -> lightPos[1] += 0.5f;
                    case GLFW_KEY_E -> lightPos[1] -= 0.5f;
                }
            }
        });

        glfwSetCursorPosCallback(window, (win, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
            if (mousePressed) {
                double dx = xpos - lastMouseX;
                double dy = ypos - lastMouseY;
                cameraAngleY += dx * 0.5f;
                cameraAngleX += dy * 0.5f;
                cameraAngleX = Math.max(-89.0f, Math.min(89.0f, cameraAngleX));
                lastMouseX = xpos;
                lastMouseY = ypos;
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                if (action == GLFW_PRESS) {
                    mousePressed = true;
                    lastMouseX = mouseX;
                    lastMouseY = mouseY;
                } else if (action == GLFW_RELEASE) mousePressed = false;
            }
        });

        glfwSetScrollCallback(window, (win, xoff, yoff) -> {
            cameraDistance -= (float) yoff * 0.5f;
            cameraDistance = Math.max(5.0f, Math.min(30.0f, cameraDistance));
        });

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> {
            width = w; height = h;
        });
    }

    private void setupLighting() {
        FloatBuffer ambient = BufferUtils.createFloatBuffer(4).put(new float[]{0.3f, 0.3f, 0.3f, 1.0f});
        FloatBuffer diffuse = BufferUtils.createFloatBuffer(4).put(new float[]{0.8f, 0.8f, 0.8f, 1.0f});
        FloatBuffer specular = BufferUtils.createFloatBuffer(4).put(new float[]{1.0f, 1.0f, 1.0f, 1.0f});
        ambient.flip(); diffuse.flip(); specular.flip();

        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_AMBIENT, ambient);
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_DIFFUSE, diffuse);
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_SPECULAR, specular);
    }

    private void loop() {
        glClearColor(0.1f, 0.1f, 0.15f, 1.0f);
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
            setupProjection();
            setupCamera();
            updateLight();

            if (spinning) objectRotation += 0.5f;

            switch (displayMode) {
                case 0 -> renderNormalScene();
                case 1 -> renderWithClipping();
                case 2 -> renderWithStencil();
                case 3 -> renderWithShadow();
                case 4 -> renderWithMirror();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void setupProjection() {
        glViewport(0, 0, width, height);
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = (float) width / height;
        float fov = 45.0f, near = 0.1f, far = 100.0f;
        float top = near * (float) Math.tan(Math.toRadians(fov) / 2.0);
        float right = top * aspect;
        GL11.glFrustum(-right, right, -top, top, near, far);
        glMatrixMode(GL_MODELVIEW);
    }

    private void setupCamera() {
        glLoadIdentity();
        float camX = cameraDistance * (float) Math.cos(Math.toRadians(cameraAngleX)) * (float) Math.sin(Math.toRadians(cameraAngleY));
        float camY = cameraDistance * (float) Math.sin(Math.toRadians(cameraAngleX));
        float camZ = cameraDistance * (float) Math.cos(Math.toRadians(cameraAngleX)) * (float) Math.cos(Math.toRadians(cameraAngleY));

        FloatBuffer matrix = BufferUtils.createFloatBuffer(16);
        float[] m = {1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
        matrix.put(m).flip();
        GL11.glMultMatrixf(matrix);
        GL11.glTranslatef(-camX, -camY, -camZ);
    }

    private void updateLight() {
        FloatBuffer pos = BufferUtils.createFloatBuffer(4).put(lightPos);
        pos.flip();
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, pos);
    }

    // ====== РЕНДЕРИНГ СЦЕН ======

    private void renderNormalScene() {
        drawFloor();
        drawMainObject(0, 1, 0);
        drawLightIndicator();
    }

    private void renderWithClipping() {
        if (useClipPlane) {
            DoubleBuffer planeBuf = BufferUtils.createDoubleBuffer(4).put(clipPlane);
            planeBuf.flip();
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0, planeBuf);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
        }

        drawFloor();
        drawMainObject(0, 1, 0);

        if (useClipPlane) GL11.glDisable(GL11.GL_CLIP_PLANE0);
        drawLightIndicator();
    }

    private void renderWithStencil() {
        glEnable(GL_STENCIL_TEST);
        glClear(GL_STENCIL_BUFFER_BIT);
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        glColorMask(false, false, false, false);
        glDepthMask(false);

        if (showStencilPattern) drawStencilPattern();
        else drawStencilMask();

        glColorMask(true, true, true, true);
        glDepthMask(true);
        glStencilFunc(GL_EQUAL, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

        drawFloor();
        drawMainObject(0, 1, 0);

        glDisable(GL_STENCIL_TEST);
        drawLightIndicator();
    }

    private void renderWithShadow() {
        drawFloor();
        drawMainObject(0, 1, 0);

        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(0, 0, 0, 0.5f);

        GL11.glPushMatrix();
        FloatBuffer shadowBuf = BufferUtils.createFloatBuffer(16).put(createShadowMatrix(lightPos, floorPlane));
        shadowBuf.flip();
        GL11.glMultMatrixf(shadowBuf);
        // Draw shadow geometry without changing color (keep black)
        drawMainObjectNoColor(0, 1, 0);
        GL11.glPopMatrix();

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_LIGHTING);
        drawLightIndicator();
    }

    private void renderWithMirror() {
        drawFloor();

        GL11.glEnable(GL11.GL_STENCIL_TEST);
        glClear(GL_STENCIL_BUFFER_BIT);
        glStencilFunc(GL_ALWAYS, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
        glColorMask(false, false, false, false);
        glDepthMask(false);

        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, -3);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(-3, 0, 0); GL11.glVertex3f(3, 0, 0);
        GL11.glVertex3f(3, 4, 0);  GL11.glVertex3f(-3, 4, 0);
        GL11.glEnd();
        GL11.glPopMatrix();

        glColorMask(true, true, true, true);
        glDepthMask(true);
        glStencilFunc(GL_EQUAL, 1, 0xFF);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);

        // Draw reflected scene within stencil using mirror plane
        GL11.glPushMatrix();
        FloatBuffer reflBuf = BufferUtils.createFloatBuffer(16).put(createReflectionMatrix(mirrorPlane));
        reflBuf.flip();
        GL11.glMultMatrixf(reflBuf);
        GL11.glFrontFace(GL11.GL_CW);

        // Reflect light position for correct shading of reflected objects
        float[] savedLight = lightPos.clone();
        float[] reflLight = reflectPointAcrossPlane(savedLight, mirrorPlane);
        FloatBuffer pos = BufferUtils.createFloatBuffer(4).put(reflLight);
        pos.flip();
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, pos);

        drawMainObject(0, 1, 0);

        // Restore light and winding
        FloatBuffer posRestore = BufferUtils.createFloatBuffer(4).put(savedLight);
        posRestore.flip();
        GL11.glLightfv(GL11.GL_LIGHT0, GL11.GL_POSITION, posRestore);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glPopMatrix();

        GL11.glDisable(GL11.GL_STENCIL_TEST);

        // Draw mirror surface overlay after reflection so it appears on top
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        glColor4f(0.7f, 0.7f, 0.9f, 0.6f);
        GL11.glPushMatrix();
        GL11.glTranslatef(0, 0, -3);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(-3, 0, 0); GL11.glVertex3f(3, 0, 0);
        GL11.glVertex3f(3, 4, 0);  GL11.glVertex3f(-3, 4, 0);
        GL11.glEnd();
        GL11.glPopMatrix();
        glDepthMask(true);
        glDisable(GL_BLEND);

        // Draw actual scene objects
        drawMainObject(0, 1, 0);
        drawLightIndicator();
    }

    // ====== ДОПОМІЖНІ ======

    private float[] createShadowMatrix(float[] light, float[] plane) {
        float[] m = new float[16];
        float dot = plane[0]*light[0]+plane[1]*light[1]+plane[2]*light[2]+plane[3]*light[3];
        m[0]=dot-light[0]*plane[0]; m[4]=-light[0]*plane[1]; m[8]=-light[0]*plane[2]; m[12]=-light[0]*plane[3];
        m[1]=-light[1]*plane[0]; m[5]=dot-light[1]*plane[1]; m[9]=-light[1]*plane[2]; m[13]=-light[1]*plane[3];
        m[2]=-light[2]*plane[0]; m[6]=-light[2]*plane[1]; m[10]=dot-light[2]*plane[2]; m[14]=-light[2]*plane[3];
        m[3]=-light[3]*plane[0]; m[7]=-light[3]*plane[1]; m[11]=-light[3]*plane[2]; m[15]=dot-light[3]*plane[3];
        return m;
    }

    private void drawMainObject(float x, float y, float z) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glRotatef(objectRotation, 0, 1, 0);
        glColor3f(0.8f, 0.3f, 0.3f);
        drawCube(1.0f);
        GL11.glPopMatrix();
    }

    private void drawMainObjectNoColor(float x, float y, float z) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, z);
        GL11.glRotatef(objectRotation, 0, 1, 0);
        // Do not set color here; use current color (e.g., black for shadow)
        drawCube(1.0f);
        GL11.glPopMatrix();
    }

    private float[] createReflectionMatrix(float[] plane) {
        float a = plane[0], b = plane[1], c = plane[2], d = plane[3];
        float len = (float)Math.sqrt(a*a + b*b + c*c);
        if (len == 0) len = 1f;
        a /= len; b /= len; c /= len; d /= len;
        float[] m = new float[16];
        m[0] = 1 - 2*a*a;  m[4] = -2*a*b;     m[8]  = -2*a*c;     m[12] = -2*a*d;
        m[1] = -2*b*a;     m[5] = 1 - 2*b*b;  m[9]  = -2*b*c;     m[13] = -2*b*d;
        m[2] = -2*c*a;     m[6] = -2*c*b;     m[10] = 1 - 2*c*c;  m[14] = -2*c*d;
        m[3] = 0;          m[7] = 0;          m[11] = 0;          m[15] = 1;
        return m;
    }

    private float[] reflectPointAcrossPlane(float[] p, float[] plane) {
        float a = plane[0], b = plane[1], c = plane[2], d = plane[3];
        float len = (float)Math.sqrt(a*a + b*b + c*c);
        if (len == 0) len = 1f;
        a /= len; b /= len; c /= len; d /= len;
        float dot = a*p[0] + b*p[1] + c*p[2] + d;
        return new float[]{ p[0] - 2*dot*a, p[1] - 2*dot*b, p[2] - 2*dot*c, p[3] };
    }

    private void drawCube(float s) {
        float half = s/2;
        GL11.glBegin(GL11.GL_QUADS);
        glVertex3f(-half,-half,half); glVertex3f(half,-half,half); glVertex3f(half,half,half); glVertex3f(-half,half,half);
        glVertex3f(-half,-half,-half); glVertex3f(-half,half,-half); glVertex3f(half,half,-half); glVertex3f(half,-half,-half);
        GL11.glEnd();
    }

    private void drawFloor() {
        glColor3f(0.4f,0.4f,0.5f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3f(-10,0,-10); GL11.glVertex3f(10,0,-10);
        GL11.glVertex3f(10,0,10);   GL11.glVertex3f(-10,0,10);
        GL11.glEnd();
    }

    private void drawLightIndicator() {
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glColor3f(1,1,0);
        GL11.glPushMatrix();
        GL11.glTranslatef(lightPos[0], lightPos[1], lightPos[2]);
        drawSphere(0.2f,8,8);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_LIGHTING);
    }

    private void drawSphere(float r, int slices, int stacks) {
        for(int i=0;i<stacks;i++){
            float lat0=(float)Math.PI*(-0.5f+(float)i/stacks);
            float z0=r*(float)Math.sin(lat0); float r0=r*(float)Math.cos(lat0);
            float lat1=(float)Math.PI*(-0.5f+(float)(i+1)/stacks);
            float z1=r*(float)Math.sin(lat1); float r1=r*(float)Math.cos(lat1);
            GL11.glBegin(GL11.GL_QUAD_STRIP);
            for(int j=0;j<=slices;j++){
                float lng=2*(float)Math.PI*j/slices; float x=(float)Math.cos(lng); float y=(float)Math.sin(lng);
                GL11.glVertex3f(x*r0,y*r0,z0);
                GL11.glVertex3f(x*r1,y*r1,z1);
            }
            GL11.glEnd();
        }
    }

    private void drawStencilMask() {
        float normX = (float)(mouseX/width)*10-5;
        float normY = (float)(1-mouseY/height)*6-1;
        GL11.glPushMatrix();
        GL11.glTranslatef(normX,normY,0);
        drawCircle(2.0f,32);
        GL11.glPopMatrix();
    }

    private void drawStencilPattern() {
        GL11.glBegin(GL11.GL_QUADS);
        for(int i=-5;i<5;i++){
            for(int j=0;j<4;j++){
                if((i+j)%2==0){
                    GL11.glVertex3f(i,j,-5);
                    GL11.glVertex3f(i+1,j,-5);
                    GL11.glVertex3f(i+1,j+1,-5);
                    GL11.glVertex3f(i,j+1,-5);
                }
            }
        }
        GL11.glEnd();
    }

    private void drawCircle(float r,int seg){
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex3f(0,0,0);
        for(int i=0;i<=seg;i++){
            float a=2.0f*(float)Math.PI*i/seg;
            GL11.glVertex3f(r*(float)Math.cos(a),r*(float)Math.sin(a),0);
        }
        GL11.glEnd();
    }
}
