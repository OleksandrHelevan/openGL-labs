import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.NULL;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.DoubleBuffer;

public class Lab8 {
    // Window and image buffers
    private long window;
    private int winWidth = 960;
    private int winHeight = 540;

    private final int baseWidth = 640;
    private final int baseHeight = 360;
    private double renderScale = 1.0; // change with Shift+Arrows
    private int imgWidth = baseWidth;
    private int imgHeight = baseHeight;
    private ByteBuffer pixelBuffer; // RGBA8
    private int screenTex;

    // Scene
    private final Scene scene = new Scene();
    private final Camera camera = new Camera();

    // Controls
    private boolean softShadows = true;
    private boolean animate = false;
    private boolean dirty = true; // re-render needed
    private boolean preview = false; // low-quality interactive mode
    private long lastInteractMs = 0;

    // Camera navigation
    private double yaw = 0.0;   // degrees, Y-up
    private double pitch = 0.0; // degrees
    private boolean keyW, keyA, keyS, keyD, keyQ, keyE;
    private boolean keyUpArr, keyDownArr, keyLeftArr, keyRightArr;
    private boolean rightMouseHeld = false;
    private double lastMouseX, lastMouseY;
    private final double mouseSensitivity = 0.2;

    // Fullscreen toggle
    private boolean isFullscreen = false;
    private int prevX = 100, prevY = 100, prevW = winWidth, prevH = winHeight;

    public static void main(String[] args) {
        new Lab8().run();
    }

    private void run() {
        initGLFW();
        initOpenGL();
        setupScene();
        updateCameraLook();

        renderImage();
        uploadTexture();

        loop();

        glfwDestroyWindow(window);
        glfwTerminate();
    }

    // <<METHODS>>
    private void initGLFW() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(winWidth, winHeight, "Lab 8 - Ray Tracing", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        glfwSetKeyCallback(window, (w, key, sc, action, mods) -> {
            if (action == GLFW_PRESS) {
                if (key == GLFW_KEY_ESCAPE) glfwSetWindowShouldClose(window, true);
                if (key == GLFW_KEY_R) { dirty = true; }
                if (key == GLFW_KEY_S) { softShadows = !softShadows; dirty = true; }
                if (key == GLFW_KEY_SPACE) { animate = !animate; }
                if (key == GLFW_KEY_F11) { toggleFullscreen(); }
                if (key == GLFW_KEY_PAGE_UP) { camera.fov = Math.max(20f, camera.fov - 2f); dirty = true; }
                if (key == GLFW_KEY_PAGE_DOWN) { camera.fov = Math.min(100f, camera.fov + 2f); dirty = true; }
                if (key == GLFW_KEY_W) keyW = true;
                if (key == GLFW_KEY_A) keyA = true;
                if (key == GLFW_KEY_S) keyS = true;
                if (key == GLFW_KEY_D) keyD = true;
                if (key == GLFW_KEY_Q) keyQ = true;
                if (key == GLFW_KEY_E) keyE = true;
                if (key == GLFW_KEY_UP) {
                    if ((mods & GLFW_MOD_SHIFT) != 0) { changeRenderScale(0.25); }
                    else if ((mods & GLFW_MOD_CONTROL) != 0) { pitch = Math.min(89.0, pitch + 3.0); updateCameraLook(); dirty = true; }
                    else keyUpArr = true;
                }
                if (key == GLFW_KEY_DOWN) {
                    if ((mods & GLFW_MOD_SHIFT) != 0) { changeRenderScale(-0.25); }
                    else if ((mods & GLFW_MOD_CONTROL) != 0) { pitch = Math.max(-89.0, pitch - 3.0); updateCameraLook(); dirty = true; }
                    else keyDownArr = true;
                }
                if (key == GLFW_KEY_LEFT) { keyLeftArr = true; }
                if (key == GLFW_KEY_RIGHT) { keyRightArr = true; }
            }
            if (action == GLFW_RELEASE) {
                if (key == GLFW_KEY_W) keyW = false;
                if (key == GLFW_KEY_A) keyA = false;
                if (key == GLFW_KEY_S) keyS = false;
                if (key == GLFW_KEY_D) keyD = false;
                if (key == GLFW_KEY_Q) keyQ = false;
                if (key == GLFW_KEY_E) keyE = false;
                if (key == GLFW_KEY_UP) keyUpArr = false;
                if (key == GLFW_KEY_DOWN) keyDownArr = false;
                if (key == GLFW_KEY_LEFT) keyLeftArr = false;
                if (key == GLFW_KEY_RIGHT) keyRightArr = false;
            }
        });

        glfwSetCursorPosCallback(window, (win, mx, my) -> {
            if (rightMouseHeld) {
                double dx = mx - lastMouseX;
                double dy = my - lastMouseY;
                yaw += dx * mouseSensitivity;
                pitch -= dy * mouseSensitivity;
                pitch = Math.max(-89.0, Math.min(89.0, pitch));
                lastMouseX = mx; lastMouseY = my;
                dirty = true;
            }
        });

        glfwSetMouseButtonCallback(window, (win, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                if (action == GLFW_PRESS) {
                    rightMouseHeld = true;
                    DoubleBuffer xb = BufferUtils.createDoubleBuffer(1);
                    DoubleBuffer yb = BufferUtils.createDoubleBuffer(1);
                    glfwGetCursorPos(window, xb, yb);
                    lastMouseX = xb.get(0); lastMouseY = yb.get(0);
                } else if (action == GLFW_RELEASE) {
                    rightMouseHeld = false;
                }
            }
        });

        glfwSetFramebufferSizeCallback(window, (win, w, h) -> { winWidth = w; winHeight = h; });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
        GL.createCapabilities();

        // Center window on screen
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pw = stack.mallocInt(1); IntBuffer ph = stack.mallocInt(1);
            glfwGetWindowSize(window, pw, ph);
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode vid = glfwGetVideoMode(monitor);
            if (vid != null) glfwSetWindowPos(window, (vid.width() - pw.get(0)) / 2, (vid.height() - ph.get(0)) / 2);
        }
    }

    private void initOpenGL() {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        screenTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, screenTex);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);

        // allocate initial buffer
        pixelBuffer = org.lwjgl.BufferUtils.createByteBuffer(imgWidth * imgHeight * 4);

        glEnable(GL_TEXTURE_2D);
    }

    private void uploadTexture() {
        glBindTexture(GL_TEXTURE_2D, screenTex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, imgWidth, imgHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer);
    }

    private void changeRenderScale(double delta) {
        double newScale = Math.max(0.5, Math.min(2.5, renderScale + delta));
        if (Math.abs(newScale - renderScale) < 1e-6) return;
        renderScale = newScale;
        resizeRenderBuffers();
    }

    private void resizeRenderBuffers() {
        // relative to renderScale
        double previewScale = 0.6;
        double s = renderScale * (preview ? previewScale : 1.0);
        imgWidth = Math.max(64, (int)Math.round(baseWidth * s));
        imgHeight = Math.max(36, (int)Math.round(baseHeight * s));
        pixelBuffer = org.lwjgl.BufferUtils.createByteBuffer(imgWidth * imgHeight * 4);
        dirty = true;
    }

    private void toggleFullscreen() {
        if (!isFullscreen) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer px = stack.mallocInt(1); IntBuffer py = stack.mallocInt(1);
                IntBuffer pw = stack.mallocInt(1); IntBuffer ph = stack.mallocInt(1);
                glfwGetWindowPos(window, px, py);
                glfwGetWindowSize(window, pw, ph);
                prevX = px.get(0); prevY = py.get(0); prevW = pw.get(0); prevH = ph.get(0);
            }
            long monitor = glfwGetPrimaryMonitor();
            GLFWVidMode vid = glfwGetVideoMode(monitor);
            if (vid != null) {
                glfwSetWindowMonitor(window, monitor, 0, 0, vid.width(), vid.height(), vid.refreshRate());
                winWidth = vid.width(); winHeight = vid.height();
                isFullscreen = true; dirty = true;
            }
        } else {
            glfwSetWindowMonitor(window, NULL, prevX, prevY, prevW, prevH, 0);
            winWidth = prevW; winHeight = prevH;
            isFullscreen = false; dirty = true;
        }
    }

    private void drawFullscreen() {
        glViewport(0, 0, winWidth, winHeight);
        glClearColor(0f, 0f, 0f, 1f);
        glClear(GL_COLOR_BUFFER_BIT);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, 1, 0, 1, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glBindTexture(GL_TEXTURE_2D, screenTex);
        glBegin(GL_QUADS);
        // Flip vertically by swapping V texcoords
        glTexCoord2f(0f, 1f); glVertex2f(0f, 0f);
        glTexCoord2f(1f, 1f); glVertex2f(1f, 0f);
        glTexCoord2f(1f, 0f); glVertex2f(1f, 1f);
        glTexCoord2f(0f, 0f); glVertex2f(0f, 1f);
        glEnd();
    }

    private void loop() {
        double t = 0.0;
        while (!glfwWindowShouldClose(window)) {
            // navigation per-frame
            boolean moved = handleMovement();
            if (moved) dirty = true;
            if (moved || rightMouseHeld) {
                enterPreview();
            }

            if (animate) {
                t += 0.016;
                for (Light l : scene.lights) {
                    l.position.x = 3.0 + Math.cos(t) * 1.5;
                    l.position.z = 5.0 + Math.sin(t * 0.7);
                }
                dirty = true;
            }

            // Leave preview after short idle
            if (preview && (System.currentTimeMillis() - lastInteractMs) > 250) {
                exitPreview();
                dirty = true;
            }

            if (dirty) {
                renderImage();
                uploadTexture();
                dirty = false;
            }
            drawFullscreen();
            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void enterPreview() {
        lastInteractMs = System.currentTimeMillis();
        if (!preview) {
            preview = true;
            resizeRenderBuffers();
        }
    }

    private void exitPreview() {
        if (preview) {
            preview = false;
            resizeRenderBuffers();
        }
    }

    // <<INNER_CLASSES>>
    static class Scene {
        java.util.List<Shape> shapes = new java.util.ArrayList<>();
        java.util.List<Light> lights = new java.util.ArrayList<>();
        Hit intersect(Ray r) {
            Hit best = new Hit(); best.t = 1.0E9;
            for (Shape s : shapes) {
                Hit h = s.intersect(r, 1.0E-4, best.t);
                if (h.hit && h.t < best.t) best = h;
            }
            return best;
        }
        boolean occluded(Ray r, double maxDist) {
            for (Shape s : shapes) {
                Hit h = s.intersect(r, 1e-4, maxDist);
                if (h.hit) return true;
            }
            return false;
        }
        Vec3 background(Ray r) {
            Vec3 d = r.direction.normalized();
            double t = 0.5 * (d.y + 1.0);
            return new Vec3(0.6, 0.8, 1.0).mul(t).add(new Vec3(1.0,1.0,1.0).mul(1.0 - t)).mul(0.8);
        }
    }

    static class Camera {
        Vec3 eye = new Vec3(0,1,6);
        Vec3 lookAt = new Vec3(0,1,0);
        Vec3 up = new Vec3(0,1,0);
        float fov = 60f;
        Vec3 u, v, w; double halfHeight, halfWidth;
        void update(int width, int height) {
            double aspect = (double)width/height;
            halfHeight = Math.tan(Math.toRadians(fov*0.5));
            halfWidth = aspect * halfHeight;
            w = eye.sub(lookAt).normalized();
            u = up.cross(w).normalized();
            v = w.cross(u);
        }
        Ray generateRay(double sx, double sy) {
            Vec3 dir = u.mul((2*sx-1)*halfWidth).add(v.mul((1-2*sy)*halfHeight)).sub(w).normalized();
            return new Ray(eye, dir);
        }
    }

    static class Light {
        Vec3 position; Vec3 color; double radius=0.0; double constant=1.0, linear=0.0, quadratic=0.0;
        Light(Vec3 p, Vec3 c){position=p;color=c;}
        Vec3 samplePosition(java.util.Random rng){
            if (radius<=0.0) return position;
            double r = radius * Math.sqrt(rng.nextDouble());
            double theta = 2.0*Math.PI*rng.nextDouble();
            return new Vec3(position.x + r*Math.cos(theta), position.y, position.z + r*Math.sin(theta));
        }
    }

    interface Shape { Hit intersect(Ray r, double tMin, double tMax); default Vec3 albedoAt(Vec3 p){return getMaterial().albedoAt(p);} Material getMaterial(); }
    static class Hit { boolean hit; double t; Vec3 position; Vec3 normal; Material material; }
    static class Material {
        Vec3 albedo = new Vec3(0.8,0.8,0.8); double kd=0.8, ks=0.2, shininess=64; double reflectivity=0.0, glossyRoughness=0.0; double refractivity=0.0, ior=1.5;
        Material(){} Material(Vec3 a){albedo=a;} Vec3 albedoAt(Vec3 p){return albedo;}
    }

    static class Sphere implements Shape {
        Vec3 c; double r; Material m; Sphere(Vec3 c,double r,Material m){this.c=c;this.r=r;this.m=m;}
        public Hit intersect(Ray ray,double tMin,double tMax){
            Vec3 oc = ray.origin.sub(c);
            double a = ray.direction.dot(ray.direction);
            double b = 2.0*oc.dot(ray.direction);
            double c2 = oc.dot(oc)-r*r; double disc=b*b-4*a*c2; Hit h=new Hit(); if(disc<0) return h;
            double s=Math.sqrt(disc); double t=(-b - s)/(2*a); if(t<tMin||t>tMax){ t=(-b + s)/(2*a); if(t<tMin||t>tMax) return h; }
            h.hit=true; h.t=t; h.position=ray.at(t); h.normal=h.position.sub(c).div(r).normalized(); h.material=m; return h;
        }
        public Material getMaterial(){return m;}
    }

    static class Plane implements Shape { Vec3 n; double d; Material m; Plane(Vec3 n,double d,Material m){this.n=n.normalized();this.d=d;this.m=m;}
        public Hit intersect(Ray ray,double tMin,double tMax){ double denom=n.dot(ray.direction); Hit h=new Hit(); if(Math.abs(denom)<1e-6) return h; double t=(d - n.dot(ray.origin))/denom; if(t<tMin||t>tMax) return h; h.hit=true; h.t=t; h.position=ray.at(t); h.normal=denom<0?n:n.neg(); h.material=m; return h; }
        public Material getMaterial(){return m;}
        public Vec3 albedoAt(Vec3 p){ return m.albedoAt(p); }
    }

    static class Box implements Shape { Vec3 bmin,bmax; Material m; Box(Vec3 bmin,Vec3 bmax,Material m){this.bmin=bmin;this.bmax=bmax;this.m=m;}
        public Hit intersect(Ray ray,double tMin,double tMax){ double t0=tMin,t1=tMax; for(int i=0;i<3;i++){ double invD=1.0/ray.direction.get(i); double tNear=(bmin.get(i)-ray.origin.get(i))*invD; double tFar=(bmax.get(i)-ray.origin.get(i))*invD; if(invD<0){ double tmp=tNear; tNear=tFar; tFar=tmp; } t0=Math.max(t0,tNear); t1=Math.min(t1,tFar); if(t1<=t0) return new Hit(); } Hit h=new Hit(); h.hit=true; h.t=t0; h.position=ray.at(t0); Vec3 p=h.position; Vec3 n=new Vec3(0,0,0); double eps=1e-4; if(Math.abs(p.x-bmin.x)<eps) n=new Vec3(-1,0,0); else if(Math.abs(p.x-bmax.x)<eps) n=new Vec3(1,0,0); else if(Math.abs(p.y-bmin.y)<eps) n=new Vec3(0,-1,0); else if(Math.abs(p.y-bmax.y)<eps) n=new Vec3(0,1,0); else if(Math.abs(p.z-bmin.z)<eps) n=new Vec3(0,0,-1); else n=new Vec3(0,0,1); h.normal=n; h.material=m; return h; }
        public Material getMaterial(){return m;}
    }

    static class Cylinder implements Shape { Vec3 c; double r; double y0,y1; Material m; Cylinder(Vec3 center,double radius,double y0,double y1,Material m){this.c=center;this.r=radius;this.y0=y0;this.y1=y1;this.m=m;}
        public Hit intersect(Ray ray,double tMin,double tMax){ Vec3 ro=ray.origin.sub(c); double a=ray.direction.x*ray.direction.x + ray.direction.z*ray.direction.z; double b=2.0*(ro.x*ray.direction.x + ro.z*ray.direction.z); double cc=ro.x*ro.x+ro.z*ro.z - r*r; Hit best=new Hit(); best.t=tMax; if(Math.abs(a)>1e-8){ double disc=b*b-4*a*cc; if(disc>=0){ double s=Math.sqrt(disc); double t=(-b - s)/(2*a); for(int i=0;i<2;i++){ if(t>=tMin && t<=best.t){ Vec3 p=ray.at(t); double y=p.y - c.y; if(y>=y0 && y<=y1){ best.hit=true; best.t=t; best.position=p; Vec3 n=new Vec3(p.x-c.x,0,p.z-c.z).div(r).normalized(); if(ray.direction.dot(n)>0) n=n.neg(); best.normal=n; best.material=m; } } t=(-b + s)/(2*a); } } } double[] ys=new double[]{y0,y1}; for(double yplane:ys){ double t=(c.y + yplane - ray.origin.y)/ray.direction.y; if(t>=tMin && t<=best.t){ Vec3 p=ray.at(t); Vec3 d=p.sub(new Vec3(c.x,c.y,c.z)); if(d.x*d.x + d.z*d.z <= r*r + 1e-6){ best.hit=true; best.t=t; best.position=p; Vec3 n=new Vec3(0, yplane==y1?1:-1, 0); if(ray.direction.dot(n)>0) n=n.neg(); best.normal=n; best.material=m; } } } return best.hit?best:new Hit(); }
        public Material getMaterial(){return m;}
    }

    static class Cone implements Shape { Vec3 c; double r; double h; Material m; Cone(Vec3 baseCenter,double radius,double height,Material m){this.c=baseCenter;this.r=radius;this.h=height;this.m=m;}
        public Hit intersect(Ray ray,double tMin,double tMax){ Vec3 ro=ray.origin.sub(new Vec3(c.x,c.y,c.z)); Vec3 rd=ray.direction; double k=r/h; double k2=k*k; double a=rd.x*rd.x + rd.z*rd.z - k2*rd.y*rd.y; double b=2*(ro.x*rd.x + ro.z*rd.z - k2*ro.y*rd.y); double cc=ro.x*ro.x + ro.z*ro.z - k2*ro.y*ro.y; Hit best=new Hit(); best.t=tMax; if(Math.abs(a)>1e-8){ double disc=b*b-4*a*cc; if(disc>=0){ double s=Math.sqrt(disc); double t=(-b - s)/(2*a); for(int i=0;i<2;i++){ if(t>=tMin && t<=best.t){ Vec3 p=ray.at(t); double y=p.y - c.y; if(y>=0 && y<=h){ best.hit=true; best.t=t; best.position=p; Vec3 pl=p.sub(c); Vec3 n=new Vec3(pl.x, -k2*pl.y, pl.z).normalized(); if(ray.direction.dot(n)>0) n=n.neg(); best.normal=n; best.material=m; } } t=(-b + s)/(2*a); } } } double t=(c.y - ray.origin.y)/ray.direction.y; if(t>=tMin && t<=best.t){ Vec3 p=ray.at(t); if(p.sub(new Vec3(c.x,c.y,c.z)).xzLength2() <= r*r + 1e-6){ best.hit=true; best.t=t; best.position=p; Vec3 n=new Vec3(0,-1,0); if(ray.direction.dot(n)>0) n=n.neg(); best.normal=n; best.material=m; } } return best.hit?best:new Hit(); }
        public Material getMaterial(){return m;}
    }

    static class Ray { Vec3 origin,direction; Ray(Vec3 o,Vec3 d){origin=o;direction=d.normalized();} Vec3 at(double t){return origin.add(direction.mul(t));} }
    static class Vec3 { double x,y,z; Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;} Vec3 add(Vec3 o){return new Vec3(x+o.x,y+o.y,z+o.z);} Vec3 sub(Vec3 o){return new Vec3(x-o.x,y-o.y,z-o.z);} Vec3 mul(double s){return new Vec3(x*s,y*s,z*s);} Vec3 mul(Vec3 o){return new Vec3(x*o.x,y*o.y,z*o.z);} Vec3 div(double s){return new Vec3(x/s,y/s,z/s);} double dot(Vec3 o){return x*o.x+y*o.y+z*o.z;} Vec3 cross(Vec3 o){return new Vec3(y*o.z - z*o.y, z*o.x - x*o.z, x*o.y - y*o.x);} double length(){return Math.sqrt(x*x+y*y+z*z);} Vec3 normalized(){double l=length(); return l==0? new Vec3(0,0,0):div(l);} Vec3 neg(){return new Vec3(-x,-y,-z);} double get(int i){return i==0?x:(i==1?y:z);} double xzLength2(){return x*x+z*z;} }
    private Vec3 trace(Ray ray, int depth, java.util.Random rng) {
        int maxDepth = preview ? 3 : 5;
        if (depth > maxDepth) return new Vec3(0,0,0);
        Hit hit = scene.intersect(ray);
        if (!hit.hit) return scene.background(ray);

        Vec3 p = hit.position;
        Vec3 n = hit.normal;
        Material m = hit.material;
        Vec3 base = m.albedoAt(p);
        Vec3 color = base.mul(0.05);

        for (Light light : scene.lights) {
            int samples = (softShadows && light.radius > 0.0) ? (preview ? 4 : 16) : 1;
            Vec3 accum = new Vec3(0,0,0);
            for (int i=0;i<samples;i++) {
                Vec3 lp = light.samplePosition(rng);
                Vec3 L = lp.sub(p);
                double dist = L.length();
                L = L.div(dist);
                if (!scene.occluded(new Ray(p.add(n.mul(1e-4)), L), dist - 2e-4)) {
                    double atten = 1.0 / (light.constant + light.linear * dist + light.quadratic * dist * dist);
                    double ndotl = Math.max(0.0, n.dot(L));
                    Vec3 diff = base.mul(m.kd * ndotl);
                    Vec3 V = ray.direction.neg();
                    Vec3 H = V.add(L).normalized();
                    double spec = Math.pow(Math.max(0.0, n.dot(H)), m.shininess) * m.ks;
                    Vec3 contrib = diff.add(new Vec3(spec, spec, spec)).mul(light.color).mul(atten);
                    accum = accum.add(contrib);
                }
            }
            color = color.add(accum.div(samples));
        }

        Vec3 V = ray.direction.neg();
        double kr = fresnel(V, n, m.ior);
        Vec3 refl = new Vec3(0,0,0);
        if (m.reflectivity > 0.0 || kr > 0.0) {
            int glossySamples = m.glossyRoughness > 0.0 ? (preview ? 2 : 8) : 1;
            for (int i=0;i<glossySamples;i++) {
                Vec3 R = reflect(ray.direction, n);
                if (m.glossyRoughness > 0.0) R = sampleHemisphereCone(R, m.glossyRoughness, rng);
                refl = refl.add(trace(new Ray(p.add(n.mul(1e-4)), R), depth+1, rng));
            }
            refl = refl.div(glossySamples);
        }
        Vec3 refr = new Vec3(0,0,0);
        if (m.refractivity > 0.0) {
            boolean into = n.dot(V) > 0;
            double n1 = into ? 1.0 : m.ior;
            double n2 = into ? m.ior : 1.0;
            Vec3 nn = into ? n : n.neg();
            Vec3 T = refract(ray.direction, nn, n1, n2);
            if (T != null) {
                refr = trace(new Ray(p.add(T.mul(1e-4)), T), depth+1, rng);
            } else {
                kr = 1.0;
            }
        }
        double reflectW = m.reflectivity * (m.refractivity > 0.0 ? kr : 1.0);
        double refractW = m.refractivity * (1.0 - kr);
        color = color.add(refl.mul(reflectW));
        color = color.add(refr.mul(refractW));
        return color;
    }

    // Compute look direction from yaw/pitch and update camera.lookAt
    private void updateCameraLook() {
        double cy = Math.cos(Math.toRadians(yaw));
        double sy = Math.sin(Math.toRadians(yaw));
        double cp = Math.cos(Math.toRadians(pitch));
        double sp = Math.sin(Math.toRadians(pitch));
        Vec3 dir = new Vec3(sy * cp, sp, -cy * cp); // Y-up
        camera.lookAt = camera.eye.add(dir);
    }

    // Move using WASD + QE; returns true if moved/rotated
    private boolean handleMovement() {
        boolean changed = false;
        double step = 0.15;
        // Direction vectors from yaw (ignore pitch for horizontal move)
        double cy = Math.cos(Math.toRadians(yaw));
        double sy = Math.sin(Math.toRadians(yaw));
        Vec3 forward = new Vec3(sy, 0, -cy); // -Z forward when yaw=0 -> forward along -Z
        Vec3 right = new Vec3(cy, 0, sy);
        if (keyW || keyUpArr) { camera.eye = camera.eye.add(forward.mul(step)); changed = true; }
        if (keyS || keyDownArr) { camera.eye = camera.eye.sub(forward.mul(step)); changed = true; }
        if (keyA) { camera.eye = camera.eye.sub(right.mul(step)); changed = true; }
        if (keyD) { camera.eye = camera.eye.add(right.mul(step)); changed = true; }
        if (keyQ) { camera.eye = camera.eye.add(new Vec3(0, step, 0)); changed = true; }
        if (keyE) { camera.eye = camera.eye.add(new Vec3(0, -step, 0)); changed = true; }
        double turnSpeed = 2.5;
        if (keyLeftArr) { yaw -= turnSpeed; changed = true; }
        if (keyRightArr) { yaw += turnSpeed; changed = true; }
        if (changed || rightMouseHeld) {
            updateCameraLook();
        }
        return changed;
    }

    private static Vec3 sampleHemisphereCone(Vec3 dir, double roughness, java.util.Random rng) {
        double coneAngle = Math.max(1e-3, roughness * 0.5);
        Vec3 w = dir.normalized();
        Vec3 u = (Math.abs(w.x) > 0.1 ? new Vec3(0,1,0) : new Vec3(1,0,0)).cross(w).normalized();
        Vec3 v = w.cross(u);
        double xi1 = rng.nextDouble();
        double xi2 = rng.nextDouble();
        double cosTheta = 1.0 - xi1 * (1.0 - Math.cos(coneAngle));
        double sinTheta = Math.sqrt(Math.max(0.0, 1.0 - cosTheta * cosTheta));
        double phi = 2.0 * Math.PI * xi2;
        Vec3 local = new Vec3(Math.cos(phi) * sinTheta, Math.sin(phi) * sinTheta, cosTheta);
        return u.mul(local.x).add(v.mul(local.y)).add(w.mul(local.z)).normalized();
    }

    private static double fresnel(Vec3 V, Vec3 N, double n2) {
        double cosi = clamp(N.dot(V), -1.0, 1.0);
        double etai = 1.0, etat = n2;
        if (cosi > 0) { double tmp = etai; etai = etat; etat = tmp; }
        double sint = etai / etat * Math.sqrt(Math.max(0.0, 1 - cosi * cosi));
        if (sint >= 1.0) return 1.0;
        double cost = Math.sqrt(Math.max(0.0, 1 - sint * sint));
        cosi = Math.abs(cosi);
        double Rs = ((etat * cosi) - (etai * cost)) / ((etat * cosi) + (etai * cost));
        double Rp = ((etai * cosi) - (etat * cost)) / ((etai * cosi) + (etat * cost));
        return (Rs*Rs + Rp*Rp) * 0.5;
    }

    private static Vec3 reflect(Vec3 I, Vec3 N) { return I.sub(N.mul(2.0 * I.dot(N))).normalized(); }
    private static Vec3 refract(Vec3 I, Vec3 N, double n1, double n2) {
        double eta = n1 / n2;
        double cosi = -Math.max(-1.0, Math.min(1.0, I.dot(N)));
        double k = 1.0 - eta * eta * (1.0 - cosi * cosi);
        if (k < 0) return null;
        return I.mul(eta).add(N.mul(eta * cosi - Math.sqrt(k))).normalized();
    }
    private static double clamp(double v, double a, double b) { return Math.max(a, Math.min(b, v)); }
    private void setupScene() {
        camera.eye = new Vec3(0, 1.2, 6.5);
        camera.lookAt = new Vec3(0, 1.0, 0);
        camera.up = new Vec3(0, 1, 0);
        camera.fov = 55f;
        yaw = 0.0; // face towards -Z
        pitch = 0.0;

        Material matteRed = new Material(new Vec3(0.9, 0.2, 0.2));
        matteRed.kd = 0.8; matteRed.ks = 0.1; matteRed.shininess = 32;

        Material matteGreen = new Material(new Vec3(0.2, 0.8, 0.2));
        matteGreen.kd = 0.8; matteGreen.ks = 0.1; matteGreen.shininess = 16;

        Material glossyMirror = new Material(new Vec3(0.9, 0.9, 0.95));
        glossyMirror.kd = 0.05; glossyMirror.ks = 0.9; glossyMirror.shininess = 128;
        glossyMirror.reflectivity = 0.8; glossyMirror.glossyRoughness = 0.05;

        Material glass = new Material(new Vec3(0.98, 0.98, 1.0));
        glass.kd = 0.05; glass.ks = 0.9; glass.shininess = 200;
        glass.reflectivity = 0.04; glass.refractivity = 0.96; glass.ior = 1.5;

        Material floorMat = new Material(new Vec3(1.0, 1.0, 1.0));
        floorMat.kd = 0.9; floorMat.ks = 0.1; floorMat.shininess = 32; floorMat.reflectivity = 0.15;

        scene.shapes.add(new Plane(new Vec3(0, 1, 0), 0.0, floorMat) {
            @Override public Vec3 albedoAt(Vec3 p) {
                int cx = (int)Math.floor(p.x * 0.5);
                int cz = (int)Math.floor(p.z * 0.5);
                boolean black = ((cx + cz) & 1) == 0;
                double v = black ? 0.12 : 0.85;
                return new Vec3(v, v, v);
            }
        });
        scene.shapes.add(new Sphere(new Vec3(-1.2, 1.0, 0.0), 1.0, matteRed));
        scene.shapes.add(new Sphere(new Vec3(1.2, 1.0, 0.6), 1.0, glass));
        scene.shapes.add(new Box(new Vec3(-2.7, 0.5, 2.0), new Vec3(-1.7, 1.5, 3.0), matteGreen));
        scene.shapes.add(new Cylinder(new Vec3(2.8, 0.0, -0.3), 0.5, 0.0, 1.5, glossyMirror));
        scene.shapes.add(new Cone(new Vec3(0.0, 0.0, -2.2), 0.8, 1.6, matteGreen));

        Light l = new Light(new Vec3(3.0, 5.0, 5.0), new Vec3(1.0, 1.0, 1.0));
        l.radius = 0.6; l.constant = 1.0; l.linear = 0.09; l.quadratic = 0.032;
        scene.lights.add(l);
        Light l2 = new Light(new Vec3(-5.0, 6.0, 4.0), new Vec3(0.7, 0.8, 1.0));
        l2.radius = 0.0; l2.constant = 1.0; l2.linear = 0.14; l2.quadratic = 0.07;
        scene.lights.add(l2);
    }

    private void renderImage() {
        if (pixelBuffer == null) return;
        pixelBuffer.clear();
        camera.update(imgWidth, imgHeight);
        java.util.Random rng = new java.util.Random(1337);
        int spp = 1;
        for (int y = 0; y < imgHeight; y++) {
            for (int x = 0; x < imgWidth; x++) {
                Vec3 col = new Vec3(0,0,0);
                for (int s = 0; s < spp; s++) {
                    double u = (x + 0.5) / (double) imgWidth;
                    double v = (y + 0.5) / (double) imgHeight;
                    Ray ray = camera.generateRay(u, v);
                    col = col.add(trace(ray, 0, rng));
                }
                col = col.div(spp);
                col = new Vec3(Math.pow(clamp(col.x, 0, 1), 1/2.2),
                               Math.pow(clamp(col.y, 0, 1), 1/2.2),
                               Math.pow(clamp(col.z, 0, 1), 1/2.2));
                pixelBuffer.put((byte)(int)(col.x*255)).put((byte)(int)(col.y*255)).put((byte)(int)(col.z*255)).put((byte)255);
            }
        }
        pixelBuffer.flip();
    }
}
