import java.lang.reflect.Method;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.zip.ZipEntry;

public class Reflect {
    public static void main(String[] args) throws Exception {
        File f = new File("C:\\Users\\msika\\.gradle\\caches\\fabric-loom\\1.21.1\\net.fabricmc.yarn.1_21_1.1.21.1+build.2-v2\\minecraft-project-@-merged-named.jar");
        URL[] urls = { f.toURI().toURL() };
        URLClassLoader cl = new URLClassLoader(urls);
        Class<?> cls = cl.loadClass("net.minecraft.client.renderer.LevelRenderer");
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getName().toLowerCase().contains("transparency") || m.getReturnType().getName().contains("PostChain")) {
                System.out.println(m.getName() + " -> " + m.getReturnType().getName());
            }
        }
    }
}
