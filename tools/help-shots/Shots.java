import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * 把手机截图裁成帮助页里用的图。
 *
 * 用法：java Shots.java <截图目录> <输出目录>
 *
 * 按 JOBS 里的框从 1080 宽的整屏图裁一块，原尺寸压成 JPEG，输出到仓库根的 help-shots/，
 * 由 shared 的构建当资源拷进包里（见 shared/build.gradle.kts）。裁完不缩放：
 * 手机上看这一页就是满屏宽，1:1 的图最清楚。
 *
 * 裁剪框都避开了状态栏、昵称 / 邮箱 / 账号名、等级这些不该进仓库的东西。
 */
public class Shots {

    /** {输出名, 源截图, x, y, w, h}。坐标按 1080 宽的原始截图量。 */
    private static final Object[][] JOBS = {
        {"settings-top.jpg", "03-settings-top.png", 0, 130, 1080, 1350},
        {"settings-mid.jpg", "04-settings-mid.png", 0, 185, 1080, 1765},
        {"settings-low.jpg", "05-settings-mid2.png", 0, 140, 1080, 1815},
        {"mine.jpg", "09-mine-entries.png", 0, 700, 1080, 1260},
        {"category.jpg", "02-category.png", 0, 150, 1080, 682},
    };

    /** 82 够看清小字，体积也不至于离谱。 */
    private static final int QUALITY = 82;

    public static void main(String[] args) throws Exception {
        File srcDir = new File(args.length > 0 ? args[0] : ".");
        File outDir = new File(args.length > 1 ? args[1] : ".");
        outDir.mkdirs();

        int total = 0;
        for (Object[] job : JOBS) {
            String name = (String) job[0];
            File src = new File(srcDir, (String) job[1]);
            if (!src.isFile()) { System.out.println("缺图：" + src); continue; }
            BufferedImage image = ImageIO.read(src);
            int x = (Integer) job[2], y = (Integer) job[3], w = (Integer) job[4], h = (Integer) job[5];
            if (y + h > image.getHeight()) h = image.getHeight() - y;

            BufferedImage cropped = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            cropped.getGraphics().drawImage(image.getSubimage(x, y, w, h), 0, 0, null);

            byte[] bytes = jpeg(cropped, QUALITY);
            java.nio.file.Files.write(new File(outDir, name).toPath(), bytes);
            total += bytes.length;
            System.out.printf("%-18s %dx%d  %d 字节%n", name, w, h, bytes.length);
        }
        System.out.printf("合计 %d 字节（%d KB）%n", total, total / 1024);
    }

    private static byte[] jpeg(BufferedImage image, int quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(stream);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality / 100f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bos.toByteArray();
    }
}
