import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Run with JDK 17: java -Djava.awt.headless=true scripts/GenerateLauncher.java */
class GenerateLauncher {
    static final Color BG = new Color(14, 17, 23), ACCENT = new Color(255, 119, 48);
    public static void main(String[] args) throws Exception {
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        double[] scales = {1, 1.5, 2, 3, 4};
        for (int i = 0; i < densities.length; i++) {
            File dir = new File("app/src/main/res/mipmap-" + densities[i]);
            dir.mkdirs();
            render(new File(dir, "banner.png"), 160, 90, scales[i], true);
            render(new File(dir, "icon.png"), 80, 80, scales[i], false);
        }
    }
    static void render(File file, int w, int h, double scale, boolean banner) throws Exception {
        BufferedImage image = new BufferedImage((int)(w*scale), (int)(h*scale), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.scale(scale, scale);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        if (banner) g.fillRect(0, 0, w, h);
        else g.fill(new RoundRectangle2D.Float(4,4,w-8,h-8,18,18));
        if (banner) {
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            FontMetrics fm = g.getFontMetrics();
            float start = (w - (22 + fm.stringWidth("hackertube"))) / 2f;
            play(g, start, 36, 18);
            float x = start + 22;
            g.setColor(Color.WHITE); g.drawString("hacker", x, 50);
            g.setColor(ACCENT); g.drawString("tube", x + fm.stringWidth("hacker"), 50);
        } else {
            play(g, 23, 23, 34);
        }
        g.dispose(); ImageIO.write(image, "png", file);
    }
    static void play(Graphics2D g, float x, float y, float size) {
        g.setColor(ACCENT); g.fill(new RoundRectangle2D.Float(x,y,size,size,size*.25f,size*.25f));
        Path2D.Float p = new Path2D.Float();
        p.moveTo(x+size*.38f,y+size*.25f);p.lineTo(x+size*.75f,y+size*.5f);p.lineTo(x+size*.38f,y+size*.75f);p.closePath();
        g.setColor(Color.WHITE);g.fill(p);
    }
}
