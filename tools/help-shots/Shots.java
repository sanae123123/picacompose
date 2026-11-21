import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.util.Base64;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * 把真机截屏裁一裁、缩一缩、压成 JPEG，再 base64 内嵌成 Kotlin 常量（HelpShots.kt）。
 *
 * 帮助页要跟着 APK 走、还得离线可看，所以图片不打进资源目录，直接编成字符串常量塞在代码里：
 * 一份 HTML 在安卓、桌面、iOS 上都能显示，不用给每个平台各配一套资源读取。
 *
 * 用法（重出一遍图）：
 *   java Shots.java <截图目录> <输出 HelpShots.kt 路径>
 * 截图目录里放这几个文件（1080x2340 的手机截屏）：02-category.png、03-settings-top.png、
 * 04-settings-mid.png、05-settings-mid2.png、09-mine-entries.png。裁剪参数见下面的 JOBS。
 *
 * 注意：截屏里凡是带昵称、邮箱、等级、签名的一律不许进这块图，发布到公开仓库前先检查。
 */
public class Shots {

    static String DIR = ".";
    static String OUT = "HelpShots.kt";

    /** 名字, 源文件, x, y, w, h（像素，按 1080x2340 的截屏量的） */
    static final Object[][] JOBS = {
        // 分类页：标题 + 热门分类第一行，避开下面带身体特写的磁贴
        {"category", "02-category.png", 0, 150, 1080, 682},
        // 设置最上面：皮肤、只看无 H、不看 AI（「保存的账号」那行不进来）
        {"settingsTop", "03-settings-top.png", 0, 130, 1080, 1350},
        // 设置中部：快速进入、线路延迟、DoH、图片 IP 优选、图片质量
        {"settingsMid", "04-settings-mid.png", 0, 185, 1080, 1765},
        // 设置靠下：优选状态、音量键、主题、检查更新、使用帮助、关于、GitHub、接口
        {"settingsLow", "05-settings-mid2.png", 0, 140, 1080, 1815},
        // 我的：四行入口 + 退出登录（上面那张资料卡裁掉了）
        {"mine", "09-mine-entries.png", 0, 700, 1080, 1260},
    };

    /** 缩到多宽。帮助页正文最宽 680px，手机上内容宽度大概 340px。 */
    static final int TARGET_W = 600;
    /** 单张 JPEG 的字节上限，超了就降质量。太大 base64 会让 APK 白胖一圈。 */
    static final int MAX_BYTES = 50000;
    static final int CHUNK = 30000;

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) DIR = args[0];
        if (args.length >= 2) OUT = args[1];
        if (!DIR.endsWith("/")) DIR = DIR + "/";

        StringBuilder kt = new StringBuilder();
        kt.append("package com.picacompose.data\n\n");
        kt.append("/*\n");
        kt.append(" * 帮助页里带的截图。原图是手机截屏，裁掉状态栏和带个人信息的部分、缩到 ")
          .append(TARGET_W).append(" px 宽、压成 JPEG 之后\n");
        kt.append(" * 直接 base64 内嵌：不依赖任何资源管线，安卓、桌面、iOS 共用同一份 HTML，离线也在。\n");
        kt.append(" *\n");
        kt.append(" * 这个文件是 tools/help-shots/Shots.java 生成的，别手改里面的 base64。\n");
        kt.append(" */\n");
        kt.append("internal object HelpShots {\n");

        for (Object[] job : JOBS) {
            String name = (String) job[0];
            String file = (String) job[1];
            int x = (Integer) job[2], y = (Integer) job[3], w = (Integer) job[4], h = (Integer) job[5];

            BufferedImage src = ImageIO.read(new File(DIR + file));
            if (src == null) throw new IllegalStateException("读不出来：" + DIR + file);
            if (y + h > src.getHeight()) h = src.getHeight() - y;

            BufferedImage crop = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            crop.createGraphics().drawImage(src.getSubimage(x, y, w, h), 0, 0, null);

            int tw = Math.min(TARGET_W, w);
            int th = Math.max(1, Math.round(h * (tw / (float) w)));
            BufferedImage small = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            var g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(crop, 0, 0, tw, th, null);
            g.dispose();

            byte[] jpg = null;
            int usedQ = 0;
            for (int q = 74; q >= 44; q -= 4) {
                byte[] attempt = jpeg(small, q / 100f);
                if (jpg == null || attempt.length < jpg.length) {
                    if (attempt.length <= MAX_BYTES) {
                        jpg = attempt;
                        usedQ = q;
                        break;
                    }
                    jpg = attempt;
                    usedQ = q;
                }
            }

            String b64 = Base64.getEncoder().encodeToString(jpg);
            kt.append("\n    /** ").append(file).append(" 裁 ").append(tw).append('x').append(th)
              .append("，JPEG q").append(usedQ).append("，")
              .append(jpg.length).append(" 字节。 */\n");
            kt.append("    val ").append(name).append(": String = DATA +\n");
            for (int i = 0; i < b64.length(); i += CHUNK) {
                String chunk = b64.substring(i, Math.min(b64.length(), i + CHUNK));
                boolean last = i + CHUNK >= b64.length();
                kt.append("        \"").append(chunk).append("\"");
                kt.append(last ? "\n" : " +\n");
            }

            System.out.printf("%-12s %dx%d  jpeg=%d  b64=%d  q=%d%n",
                    name, tw, th, jpg.length, b64.length(), usedQ);
        }

        kt.append("\n    private const val DATA = \"data:image/jpeg;base64,\"\n");
        kt.append("}\n");

        try (PrintWriter out = new PrintWriter(new File(OUT), "UTF-8")) {
            out.print(kt);
        }
        System.out.println("写出 " + OUT + "（" + kt.length() + " 字符）");
    }

    static byte[] jpeg(BufferedImage img, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(bos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        }
        writer.dispose();
        return bos.toByteArray();
    }
}
