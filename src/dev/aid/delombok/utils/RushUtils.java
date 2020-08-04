package dev.aid.delombok.utils;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import com.github.difflib.patch.PatchFailedException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 就靠你了
 *
 * @author: 04637@163.com
 * @date: 2020/8/4
 */
public class RushUtils {
    // 提取字符串前面的空字符, 即缩进indent
    private static final Pattern pattern = Pattern.compile("^(\\s*)");

    private RushUtils() {
    }

    public static void main(String[] args) {
        String srcDir = "src\\test";
        String baseDir = "E:\\projects\\qqq\\delombok";
        rush(baseDir, srcDir);
    }

    /**
     * 1. 备份 srcDir 至 tmpDir/timestamp 中
     * 2. 在 baseDir 中执行 delombok, 生成至 tmpDir/target 中
     * 3. 将 targetDir 中的代码处理后, 覆盖 srcDir
     *
     * @param baseDir rush的根目录, 所有命令将在此目录中执行
     * @param srcDir  需要 delombok 的源码目录, 注意该目录传值为 baseDir 的相对路径
     */
    public static final void rush(String baseDir, String srcDir) {
        // 如果是相对路径, 拼接绝对路径
        if (StringUtils.isEmpty(srcDir)) {
            throw new IllegalArgumentException("未指定src目录");
        } else if (srcDir.charAt(0) != '/') {
            srcDir = baseDir + "/" + srcDir;
        }
        String tmpDir = baseDir + "/delombok";
        try {
            // 1. 备份源文件目录
            long timestamp = System.currentTimeMillis();
            FileUtils.copyDirectoryToDirectory(new File(srcDir),
                    new File(tmpDir + "/bak-" + timestamp));
            // 2. 反编译lombok注解
            String targetDir = tmpDir + "/target";
            String cmd = "cmd /c " +
                    "java -cp \"lombok.jar;tools.jar\" lombok.launch.Main delombok " + srcDir
                    + " -d " + targetDir + " -e UTF-8 -f indent:4 -f generateDelombokComment:skip -f javaLangAsFQN:skip";
            Process process = Runtime.getRuntime().exec(cmd, null, new File(baseDir));
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Delombok failed!");
            }
            // 3. 遍历源码文件
            traverseDir(new File(srcDir));
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将源文件替换为目标文件, 请注意源文件需进行备份!
     *
     * @param originalPath 源文件
     * @param targetPath   目标文件, 会对其新增块包裹折叠注释
     */
    private static final void patchFile(String originalPath, String targetPath) {
        try {
            List<String> originalLines = Files.readAllLines(new File(originalPath).toPath());
            List<String> targetLines = Files.readAllLines(new File(targetPath).toPath());

            Patch<String> patch = DiffUtils.diff(originalLines, targetLines);
            for (AbstractDelta delta : patch.getDeltas()) {
                // 仅对新增块做操作
                if (delta.getType() == DeltaType.INSERT) {
                    List<String> lines = delta.getTarget().getLines();
                    int emptyIndex = 0;
                    // 自定义折叠注释缩进
                    String indent = "";
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.isEmpty()) {
                            // 获取首个非空行位置
                            emptyIndex = i + 1;
                        } else {
                            // 获取首个非空行代码的缩进
                            Matcher matcher = pattern.matcher(line);
                            if (matcher.find()) {
                                indent = matcher.group(1);
                            }
                            break;
                        }
                    }
                    // 在首个非空行位置及末尾位置插入自定义折叠注释
                    lines.add(emptyIndex, indent + "//<editor-fold desc=\"delombok\">");
                    lines.add(indent + "//</editor-fold>");
                }
            }
            List<String> result = patch.applyTo(originalLines);
            try (FileWriter fw = new FileWriter(originalPath)) {
                for (String s : result) {
                    fw.write(s + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (IOException | PatchFailedException e) {
            e.printStackTrace();
        }
    }

    private final static void traverseDir(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles();
        for (File f : files) {
            if (f.isDirectory()) {
                traverseDir(f);
            } else if (f.isFile()) {
                System.out.println(f.getPath());
            }
        }
    }

}
