// Gera o FocaEstudo.ico a partir do AppTheme.appIcon (usado pelo empacotar.ps1).
import java.io.*;
import java.nio.*;
import java.util.*;
import javax.imageio.ImageIO;

public class MakeIco {
    public static void main(String[] a) throws Exception {
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();
        for (int s : sizes) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(AppTheme.appIcon(s), "png", b);
            pngs.add(b.toByteArray());
        }
        ByteBuffer h = ByteBuffer.allocate(6 + 16 * sizes.length).order(ByteOrder.LITTLE_ENDIAN);
        h.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int off = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            h.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            h.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(off);
            off += pngs.get(i).length;
        }
        try (FileOutputStream o = new FileOutputStream(a[0])) {
            o.write(h.array());
            for (byte[] p : pngs) o.write(p);
        }
    }
}
