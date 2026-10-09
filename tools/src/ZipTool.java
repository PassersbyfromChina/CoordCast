import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Merges extra files into an APK produced by aapt2 link.
 *
 * <p>Usage: {@code ZipTool <out.zip> <base.zip> <entryName>=<file> ...}</p>
 *
 * <p>Existing entries keep their original compression method — important because
 * {@code resources.arsc} must stay uncompressed for apps targeting API 30+.
 * {@code zipalign} fixes up the alignment afterwards.</p>
 *
 * <p><b>Every entry is stamped with the same fixed date.</b> Zip timestamps are otherwise
 * the one thing that changes between two builds of identical source, which is what made
 * the APK unreproducible and the hash in the README unverifiable. The date is set through
 * {@link ZipEntry#setTimeLocal} rather than {@code setTime} because the latter converts
 * through the default timezone, so the same instant would produce different bytes on
 * different machines.</p>
 */
public final class ZipTool {

    /** 2009-01-01 00:00:00, the conventional "no date" stamp used by reproducible builds. */
    private static final LocalDateTime FIXED_TIME = LocalDateTime.of(2009, 1, 1, 0, 0, 0);

    private ZipTool() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: ZipTool <out.zip> <base.zip> [name=file ...]");
            System.exit(2);
        }
        File out = new File(args[0]);
        File base = new File(args[1]);
        File parent = out.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        byte[] buffer = new byte[1 << 16];
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)))) {
            try (ZipFile zip = new ZipFile(base)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry source = entries.nextElement();
                    ZipEntry target = new ZipEntry(source.getName());
                    target.setMethod(source.getMethod());
                    target.setTimeLocal(FIXED_TIME);
                    if (source.getMethod() == ZipEntry.STORED) {
                        target.setSize(source.getSize());
                        target.setCompressedSize(source.getCompressedSize());
                        target.setCrc(source.getCrc());
                    }
                    zos.putNextEntry(target);
                    try (InputStream in = zip.getInputStream(source)) {
                        copy(in, zos, buffer);
                    }
                    zos.closeEntry();
                }
            }

            for (int i = 2; i < args.length; i++) {
                int eq = args[i].indexOf('=');
                if (eq <= 0) {
                    throw new IllegalArgumentException("expected name=file, got: " + args[i]);
                }
                String name = args[i].substring(0, eq);
                File source = new File(args[i].substring(eq + 1));
                if (!source.isFile()) {
                    throw new IllegalArgumentException("missing input file: " + source);
                }
                ZipEntry entry = new ZipEntry(name);
                entry.setMethod(ZipEntry.DEFLATED);
                entry.setTimeLocal(FIXED_TIME);
                zos.putNextEntry(entry);
                try (InputStream in = Files.newInputStream(source.toPath())) {
                    copy(in, zos, buffer);
                }
                zos.closeEntry();
                System.out.println("  + " + name + " (" + source.length() + " bytes)");
            }
        }
        System.out.println("wrote " + out + " (" + out.length() + " bytes)");
    }

    private static void copy(InputStream in, OutputStream out, byte[] buffer) throws Exception {
        int read;
        while ((read = in.read(buffer)) > 0) {
            out.write(buffer, 0, read);
        }
    }
}
