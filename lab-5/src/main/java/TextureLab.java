
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.*;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.glWindowPos2i;
import static org.lwjgl.opengl.GL30.glGenerateMipmap;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

public class TextureLab {

    private long window;
    private int currentPart = 7;
    private float rotation = 0.0f;
    private float textureRotation = 0.0f;

    private int texture1ID;
    private int texture2ID;
    private int textureWidth, textureHeight;
    private ByteBuffer textureBuffer;

    public static void main(String[] args) {
        new TextureLab().run();
    }

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(800, 600, "Lab 5 – Textures", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key >= GLFW_KEY_1 && key <= GLFW_KEY_7) {
                    currentPart = key - GLFW_KEY_0;
                    System.out.println("🔹 Switched to part " + currentPart);
                }
                if (key == GLFW_KEY_ESCAPE)
                    glfwSetWindowShouldClose(window, true);
            }
        });

        // --- Завантаження звичайних текстур ---
        texture1ID = loadTexture("texture1.png");
        texture2ID = loadTexture("texture2.png");
        loadRawImageData();

        glEnable(GL_DEPTH_TEST);
        glEnable(GL_TEXTURE_2D);

        System.out.println("✅ Ready. Press 1–7, ESC to exit.");
    }

    private void loop() {
        glClearColor(0.1f, 0.15f, 0.25f, 1.0f);

        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            setupCamera();
            rotation += 0.5f;
            textureRotation += 0.3f;

            switch (currentPart) {
                case 3 -> renderPart3();
                case 4 -> renderPart4();
                case 5 -> renderPart5();
                case 6 -> renderPart6();
                case 7 -> renderPart7();
            }

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        glDeleteTextures(texture1ID);
        glDeleteTextures(texture2ID);
        if (textureBuffer != null) STBImage.stbi_image_free(textureBuffer);
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // --- Частини ---
    private void renderPart3() {
        glDisable(GL_TEXTURE_2D);
        glWindowPos2i(50, 50);
        glPixelZoom(1.2f, 1.2f);
        if (textureBuffer != null) {
            textureBuffer.flip();
            glDrawPixels(textureWidth, textureHeight, GL_RGBA, GL_UNSIGNED_BYTE, textureBuffer);
        }
    }

    private void renderPart4() {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, texture1ID);
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glTranslatef(0, 0, -5);
        glRotatef(rotation, 1, 1, 0);
        glScalef(1.5f, 1.5f, 1.5f);

        glMatrixMode(GL_TEXTURE);
        glPushMatrix();
        glTranslatef(0.5f, 0.5f, 0);
        glRotatef(textureRotation, 0, 0, 1);
        glTranslatef(-0.5f, -0.5f, 0);
        drawTexturedQuad();
        glPopMatrix();

        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        glDisable(GL_TEXTURE_2D);
    }

    private void renderPart5() {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, texture1ID);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glPushMatrix();
        glTranslatef(-1.5f, 0, -6);
        glRotatef(rotation, 0, 1, 0);
        drawTexturedQuad();
        glPopMatrix();

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glPushMatrix();
        glTranslatef(1.5f, 0, -6);
        glRotatef(rotation, 0, 1, 0);
        drawTexturedQuad();
        glPopMatrix();
    }

    private void renderPart6() {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, texture1ID);
        glPushMatrix();
        glTranslatef(0, 0, -6);
        glRotatef(rotation, 1, 1, 1);
        drawTexturedCube();
        glPopMatrix();
        glDisable(GL_TEXTURE_2D);
    }

    // --- ГОЛОВНЕ: Текстурована сфера ---
    private void renderPart7() {
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, texture1ID);
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        glPushMatrix();
        glTranslatef(0, 0, -4);
        glRotatef(rotation, 0.3f, 1.0f, 0.2f);
        drawSphere();
        glPopMatrix();

        glDisable(GL_TEXTURE_2D);
    }

    // --- Завантаження звичайних 2D текстур ---
    private int loadTexture(String file) {
        int id = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, id);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            ByteBuffer img = STBImage.stbi_load(getResourcePath(file), w, h, c, 4);
            if (img == null)
                throw new RuntimeException("Failed to load " + file + ": " + STBImage.stbi_failure_reason());
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w.get(0), h.get(0), 0,
                    GL_RGBA, GL_UNSIGNED_BYTE, img);
            glGenerateMipmap(GL_TEXTURE_2D);
            STBImage.stbi_image_free(img);
            System.out.println("✅ Loaded texture: " + file);
        }
        return id;
    }

    private void loadRawImageData() {
        try (MemoryStack stack = stackPush()) {
            IntBuffer w = stack.mallocInt(1), h = stack.mallocInt(1), c = stack.mallocInt(1);
            textureBuffer = STBImage.stbi_load(getResourcePath("texture1.png"), w, h, c, 4);
            if (textureBuffer == null)
                throw new RuntimeException("Failed raw load " + "texture1.png" + ": " + STBImage.stbi_failure_reason());
            textureWidth = w.get(0);
            textureHeight = h.get(0);
        }
    }

    private String getResourcePath(String fileName) {
        try {
            URL resource = TextureLab.class.getResource("/" + fileName);
            if (resource != null) return new File(resource.toURI()).getAbsolutePath();
            File local = new File(fileName);
            if (local.exists()) return local.getAbsolutePath();
            throw new RuntimeException("Cannot find: " + fileName);
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to get path for " + fileName, e);
        }
    }

    private void setupCamera() {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        float aspect = 800f / 600f;
        glFrustum(-aspect, aspect, -1, 1, 1.5, 20);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
    }

    private void drawTexturedQuad() {
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex3f(-1, -1, 0);
        glTexCoord2f(1, 0);
        glVertex3f(1, -1, 0);
        glTexCoord2f(1, 1);
        glVertex3f(1, 1, 0);
        glTexCoord2f(0, 1);
        glVertex3f(-1, 1, 0);
        glEnd();
    }

    private void drawTexturedCube() {
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex3f(-1, -1, 1);
        glTexCoord2f(1, 0);
        glVertex3f(1, -1, 1);
        glTexCoord2f(1, 1);
        glVertex3f(1, 1, 1);
        glTexCoord2f(0, 1);
        glVertex3f(-1, 1, 1);
        glEnd();
    }

    // --- Сфера з u,v координатами ---
    private void drawSphere() {
        for (int i = 0; i <= 64; i++) {
            double lat0 = Math.PI * (-0.5 + (double) (i - 1) / 64);
            double z0 = Math.sin(lat0), zr0 = Math.cos(lat0);
            double lat1 = Math.PI * (-0.5 + (double) i / 64);
            double z1 = Math.sin(lat1), zr1 = Math.cos(lat1);

            glBegin(GL_QUAD_STRIP);
            for (int j = 0; j <= 64; j++) {
                double lng = 2 * Math.PI * (double) (j - 1) / 64;
                double x = Math.cos(lng);
                double y = Math.sin(lng);

                double u = (double) j / 64;
                double v0 = (double) (i - 1) / 64;
                double v1 = (double) i / 64;

                glTexCoord2d(u, v0);
                glNormal3d(x * zr0, y * zr0, z0);
                glVertex3d(1.5 * x * zr0, 1.5 * y * zr0, 1.5 * z0);

                glTexCoord2d(u, v1);
                glNormal3d(x * zr1, y * zr1, z1);
                glVertex3d(1.5 * x * zr1, 1.5 * y * zr1, 1.5 * z1);
            }
            glEnd();
        }
    }
}
