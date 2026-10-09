import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

/**
 * Renders the legacy launcher bitmaps (API 24-25, which cannot use adaptive icons)
 * with the same visual identity as the adaptive icon: an accent gradient tile with
 * a white location arrow.
 *
 * <p>Usage: {@code IconGen <resDir>}</p>
 */
public final class IconGen {

    private static final Color TOP = new Color(0x4F, 0x8C, 0xFF);
    private static final Color BOTTOM = new Color(0x0A, 0x5B, 0xFF);

    /** Location arrow in 0..1 normalised space, matching ic_launcher_foreground. */
    private static final double[][] ARROW = {
            {0.5000, 0.2201},
            {0.7482, 0.7799},
            {0.5000, 0.6594},
            {0.2518, 0.7799},
    };

    private IconGen() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: IconGen <resDir>");
            System.exit(2);
        }
        File res = new File(args[0]);
        String[][] targets = {
                {"mipmap-mdpi", "48"},
                {"mipmap-hdpi", "72"},
                {"mipmap-xhdpi", "96"},
                {"mipmap-xxhdpi", "144"},
                {"mipmap-xxxhdpi", "192"},
        };
        for (String[] target : targets) {
            int size = Integer.parseInt(target[1]);
            File dir = new File(res, target[0]);
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IllegalStateException("cannot create " + dir);
            }
            write(render(size, false), new File(dir, "ic_launcher.png"));
            write(render(size, true), new File(dir, "ic_launcher_round.png"));
        }
        System.out.println("launcher bitmaps written to " + res.getAbsolutePath());
    }

    private static void write(BufferedImage image, File file) throws Exception {
        ImageIO.write(image, "png", file);
        System.out.println("  " + file.getParentFile().getName() + "/" + file.getName()
                + " " + image.getWidth() + "x" + image.getHeight());
    }

    /** @param round true renders a circular tile for {@code android:roundIcon} */
    private static BufferedImage render(int size, boolean round) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float s = size;
        Shape tile;
        if (round) {
            tile = new Ellipse2D.Float(0f, 0f, s, s);
        } else {
            float margin = s * 0.015f;
            float radius = s * 0.44f;
            tile = new RoundRectangle2D.Float(margin, margin, s - margin * 2, s - margin * 2,
                    radius, radius);
        }

        // Body: accent gradient running from the top-left to the bottom-right.
        g.setPaint(new LinearGradientPaint(
                new Point2D.Float(0f, 0f), new Point2D.Float(s, s),
                new float[]{0f, 1f}, new Color[]{TOP, BOTTOM}));
        g.fill(tile);

        // Specular sheen over the top half, clipped to the tile.
        Shape clip = g.getClip();
        g.clip(tile);
        g.setPaint(new GradientPaint(0f, 0f, new Color(255, 255, 255, 78),
                0f, s * 0.58f, new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, size, size);
        g.setClip(clip);

        // Location arrow, filled and stroked so its corners stay rounded.
        float shrink = round ? 0.90f : 1.0f;
        Path2D arrow = new Path2D.Float();
        for (int i = 0; i < ARROW.length; i++) {
            float x = (float) (0.5 + (ARROW[i][0] - 0.5) * shrink) * s;
            float y = (float) (0.5 + (ARROW[i][1] - 0.5) * shrink) * s;
            if (i == 0) {
                arrow.moveTo(x, y);
            } else {
                arrow.lineTo(x, y);
            }
        }
        arrow.closePath();
        g.setColor(Color.WHITE);
        g.fill(arrow);
        g.setStroke(new BasicStroke(s * 0.075f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(arrow);

        g.dispose();
        return image;
    }
}
