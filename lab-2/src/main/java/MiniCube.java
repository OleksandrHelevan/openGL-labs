import static org.lwjgl.opengl.GL11.*;

public class MiniCube {
    float x, y, z;
    float gap;

    private static final float[][][] VERTICES = {
            { {-0.5f,-0.5f, 0.5f}, {0.5f,-0.5f, 0.5f}, {0.5f,0.5f,0.5f}, {-0.5f,0.5f,0.5f} },
            { {-0.5f,-0.5f,-0.5f}, {-0.5f,0.5f,-0.5f}, {0.5f,0.5f,-0.5f}, {0.5f,-0.5f,-0.5f} },
            { {-0.5f,0.5f,-0.5f}, {-0.5f,0.5f,0.5f}, {0.5f,0.5f,0.5f}, {0.5f,0.5f,-0.5f} },
            { {-0.5f,-0.5f,-0.5f}, {0.5f,-0.5f,-0.5f}, {0.5f,-0.5f,0.5f}, {-0.5f,-0.5f,0.5f} },
            { {0.5f,-0.5f,-0.5f}, {0.5f,0.5f,-0.5f}, {0.5f,0.5f,0.5f}, {0.5f,-0.5f,0.5f} },
            { {-0.5f,-0.5f,-0.5f}, {-0.5f,-0.5f,0.5f}, {-0.5f,0.5f,0.5f}, {-0.5f,0.5f,-0.5f} }
    };

    MiniCube(float x, float y, float z, float gap) {
        this.x = x; this.y = y; this.z = z; this.gap = gap;
    }

    void draw() {
        float s = 0.9f;
        glPushMatrix();
        glTranslatef(x * (s + gap), y * (s + gap), z * (s + gap));

        for (int i = 0; i < 6; i++) {
            glBegin(GL_QUADS);
            glColor3f(0.7f, 0.7f, 0.7f);
            for (float[] v : VERTICES[i])
                glVertex3f(v[0] * s, v[1] * s, v[2] * s);
            glEnd();

            glPolygonMode(GL_FRONT_AND_BACK, GL_LINE);
            glColor3f(0,0,0);
            glBegin(GL_QUADS);
            for (float[] v : VERTICES[i])
                glVertex3f(v[0] * s, v[1] * s, v[2] * s);
            glEnd();
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
        }

        glPopMatrix();
    }
}
