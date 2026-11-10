import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * AWPM Calculator - a lightweight, Monkeytype-inspired typing speed test.
 * Pure Java Swing + Java2D. Single file: compile with `javac AWPMCalculator.java`
 * and run with `java AWPMCalculator`.
 *
 * Controls: just start typing. SPACE commits a word, BACKSPACE edits the current word,
 * TAB / ESC restarts, gear icon opens settings, Restart button restarts.
 */
public class AWPMCalculator extends JPanel {

    // ---------- Word pool ----------
    private static final String[] POOL = ("the be to of and a in that have it for not on with he as you do at this but his by "
            + "from they we say her she or an will my one all would there their what so up out if about who get which go me "
            + "when make can like time no just him know take people into year your good some could them see other than then "
            + "now look only come its over think also back after use two how our work first well way even new want because "
            + "any these give day most us ocean beyond crimson sky filled endless stars dreams never fade silence where loses "
            + "meaning thoughts become free wind across galaxy river mountain light shadow whisper golden forest silver storm "
            + "morning night dance quiet bright memory journey horizon gentle burning distant echo velvet thunder paper window "
            + "garden island bridge candle mirror harvest winter summer anchor engine rhythm compass lantern meadow harbor").split(" ");

    // ---------- Palette ----------
    private static final Color BG = new Color(0xF8F9FD);
    private static final Color CARD = new Color(0xF3F5FB);
    private static final Color CARD_BORDER = new Color(0xE6E9F2);
    private static final Color INK = new Color(0x1F2937);
    private static final Color MUTED = new Color(0x98A0B3);
    private static final Color LABEL = new Color(0x3B4357);
    private static final Color ACCENT = new Color(0x2F6BFF);
    private static final Color TRACK = new Color(0xE3E7F1);
    private static final Color ERROR = new Color(0xE5484D);
    private static final Color HILITE_FILL = new Color(0xD5E3FF);
    private static final Color HILITE_BORDER = new Color(0x7EA6FF);

    // ---------- Test state ----------
    private int duration = 60;
    private final List<String> words = new ArrayList<>();
    private final List<String> typed = new ArrayList<>();
    private final StringBuilder input = new StringBuilder();
    private int wordIdx;
    private boolean started, finished;
    private long startNanos;
    private double elapsed;
    private int totalKeys, errorKeys, uncorrectedCommitted;
    private final List<Double> samples = new ArrayList<>();
    private int lastSampleSec;

    // ---------- Hit areas ----------
    private final Rectangle restartBtn = new Rectangle();
    private final Rectangle gearBtn = new Rectangle();
    private boolean hoverRestart, hoverGear;

    public AWPMCalculator() {
        setPreferredSize(new Dimension(1100, 700));
        setBackground(BG);
        setFocusable(true);
        reset();

        addKeyListener(new KeyAdapter() {
            @Override public void keyTyped(KeyEvent e) { onKey(e.getKeyChar()); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (restartBtn.contains(e.getPoint())) reset();
                else if (gearBtn.contains(e.getPoint())) openSettings();
                requestFocusInWindow();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                hoverRestart = restartBtn.contains(e.getPoint());
                hoverGear = gearBtn.contains(e.getPoint());
                setCursor(hoverRestart || hoverGear ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
        new javax.swing.Timer(50, e -> tick()).start();
    }

    // =====================================================================
    //  Logic
    // =====================================================================
    private void reset() {
        words.clear();
        typed.clear();
        input.setLength(0);
        samples.clear();
        wordIdx = 0;
        started = finished = false;
        elapsed = 0;
        totalKeys = errorKeys = uncorrectedCommitted = 0;
        lastSampleSec = 0;
        appendWords(120);
        repaint();
    }

    private void appendWords(int n) {
        List<String> pool = new ArrayList<>(Arrays.asList(POOL));
        Collections.shuffle(pool);
        for (String w : pool) {
            if (n-- <= 0) break;
            if (!words.isEmpty() && words.get(words.size() - 1).equals(w)) continue;
            words.add(w);
        }
    }

    private void openSettings() {
        Object[] opts = {"15s", "30s", "60s", "120s"};
        int pick = JOptionPane.showOptionDialog(this, "Test duration", "Settings",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, opts, "60s");
        if (pick >= 0) {
            duration = new int[]{15, 30, 60, 120}[pick];
            reset();
        }
    }

    private void onKey(char c) {
        if (c == '\t' || c == 27) { reset(); return; }
        if (finished) return;

        if (c == '\b') {
            if (input.length() > 0) input.setLength(input.length() - 1);
        } else if (c == ' ') {
            if (input.length() == 0) return;
            begin();
            totalKeys++;
            String target = words.get(wordIdx);
            if (input.length() < target.length()) errorKeys++;
            uncorrectedCommitted += mismatch(target, input.toString());
            typed.add(input.toString());
            input.setLength(0);
            wordIdx++;
            if (wordIdx > words.size() - 40) appendWords(100);
        } else if (c > 32 && !Character.isISOControl(c)) {
            begin();
            String target = words.get(wordIdx);
            if (input.length() >= target.length() + 8) return;
            totalKeys++;
            int p = input.length();
            if (p >= target.length() || target.charAt(p) != c) errorKeys++;
            input.append(c);
        }
        repaint();
    }

    private void begin() {
        if (!started) {
            started = true;
            startNanos = System.nanoTime();
        }
    }

    private static int mismatch(String target, String t) {
        int n = Math.max(target.length(), t.length()), bad = 0;
        for (int i = 0; i < n; i++) {
            if (i >= target.length() || i >= t.length() || target.charAt(i) != t.charAt(i)) bad++;
        }
        return bad;
    }

    private void tick() {
        if (started && !finished) {
            elapsed = (System.nanoTime() - startNanos) / 1e9;
            if (elapsed >= duration) {
                elapsed = duration;
                finished = true;
                samples.add(wpm());
            } else if ((int) elapsed > lastSampleSec) {
                lastSampleSec = (int) elapsed;
                samples.add(wpm());
            }
        }
        repaint();
    }

    private int typedChars() {
        int n = input.length();
        for (String t : typed) n += t.length() + 1;
        return n;
    }

    private double minutes() { return Math.max(elapsed, 1.0) / 60.0; }

    private double wpm() {
        return started ? typedChars() / 5.0 / minutes() : 0;
    }

    private double awpm() {
        if (!started) return 0;
        int bad = uncorrectedCommitted + (wordIdx < words.size() ? mismatch(words.get(wordIdx), input.toString()) : 0);
        return Math.max(0, (typedChars() / 5.0 - bad) / minutes());
    }

    private double accuracy() {
        return totalKeys == 0 ? 100.0 : (totalKeys - errorKeys) * 100.0 / totalKeys;
    }

    // =====================================================================
    //  Painting
    // =====================================================================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int m = 40;

        drawTimer(g, w);
        drawGear(g, w - 70, 72);
        drawTextCard(g, m, 150, w - 2 * m, 290);

        int statsW = (int) ((w - 2 * m) * 0.56);
        drawStats(g, m, 460, statsW, 140);
        drawChart(g, m + statsW + 2, 460, w - 2 * m - statsW - 2, 148);
        drawRestart(g, w / 2, 645);
        g.dispose();
    }

    private void drawTimer(Graphics2D g, int w) {
        int remaining = (int) Math.ceil(duration - elapsed);
        String s = remaining + "s";
        Font f = new Font(Font.SANS_SERIF, Font.PLAIN, 54);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int iconD = 44, gap = 18;
        int total = iconD + gap + fm.stringWidth(s);
        int x = (w - total) / 2;
        int cy = 72;

        // clock icon
        g.setColor(INK);
        g.setStroke(new BasicStroke(3.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(x, cy - iconD / 2.0, iconD, iconD));
        g.draw(new Line2D.Double(x + iconD / 2.0, cy, x + iconD / 2.0, cy - 12));
        g.draw(new Line2D.Double(x + iconD / 2.0, cy, x + iconD / 2.0 + 9, cy + 5));
        g.drawString(s, x + iconD + gap, cy + fm.getAscent() / 2 - 4);

        // progress bar
        int bw = 500, bh = 8, bx = (w - bw) / 2, by = 128;
        g.setColor(TRACK);
        g.fillRoundRect(bx, by, bw, bh, bh, bh);
        double frac = Math.min(1, elapsed / duration);
        int fw = (int) Math.max(started ? 10 : 0, bw * frac);
        if (fw > 0) {
            g.setColor(ACCENT);
            g.fillRoundRect(bx, by, fw, bh, bh, bh);
        }
    }

    private void drawGear(Graphics2D g, int cx, int cy) {
        gearBtn.setBounds(cx - 24, cy - 24, 48, 48);
        g.setColor(hoverGear ? ACCENT : INK);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(cx - 9, cy - 9, 18, 18));
        g.draw(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
        for (int i = 0; i < 8; i++) {
            AffineTransform old = g.getTransform();
            g.rotate(Math.toRadians(i * 45), cx, cy);
            g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(cx, cy - 12, cx, cy - 13));
            g.setTransform(old);
        }
    }

    private void drawCard(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(CARD);
        g.fillRoundRect(x, y, w, h, 22, 22);
        g.setColor(CARD_BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(x, y, w, h, 22, 22);
    }

    private void drawTextCard(Graphics2D g, int x, int y, int w, int h) {
        drawCard(g, x, y, w, h);
        Shape oldClip = g.getClip();
        g.clip(new RoundRectangle2D.Double(x + 4, y + 4, w - 8, h - 8, 20, 20));

        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 32);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        int cw = fm.charWidth('m');
        int gap = (int) (cw * 1.7);
        int avail = w - 70;
        int lineH = 58;

        // layout words into lines
        int limit = Math.min(words.size(), wordIdx + 90);
        int[] lineOf = new int[limit];
        int[] xOf = new int[limit];
        int line = 0, cx = 0;
        for (int i = 0; i < limit; i++) {
            int ww = words.get(i).length() * cw;
            if (cx > 0 && cx + ww > avail) { line++; cx = 0; }
            lineOf[i] = line;
            xOf[i] = cx;
            cx += ww + gap;
        }

        int curLine = lineOf[Math.min(wordIdx, limit - 1)];
        int firstLine = Math.max(0, curLine - 1);
        int left = x + 35;
        int base0 = y + 62;

        for (int i = 0; i < limit; i++) {
            int ln = lineOf[i] - firstLine;
            if (ln < 0 || ln > 2) continue;
            String word = words.get(i);
            int wx = left + xOf[i];
            int base = base0 + ln * lineH;

            if (i == wordIdx) {
                int rw = word.length() * cw;
                g.setColor(HILITE_FILL);
                g.fillRoundRect(wx - 9, base - fm.getAscent() - 3, rw + 18, fm.getHeight() + 6, 12, 12);
                g.setColor(HILITE_BORDER);
                g.setStroke(new BasicStroke(1.4f));
                g.drawRoundRect(wx - 9, base - fm.getAscent() - 3, rw + 18, fm.getHeight() + 6, 12, 12);
            }

            String t = i < typed.size() ? typed.get(i) : (i == wordIdx ? input.toString() : "");
            boolean committed = i < wordIdx;
            for (int j = 0; j < word.length(); j++) {
                char ch = word.charAt(j);
                int px = wx + j * cw;
                boolean has = j < t.length();
                Color c;
                if (has) c = t.charAt(j) == ch ? INK : ERROR;
                else c = MUTED;
                g.setColor(c);
                g.drawString(String.valueOf(ch), px, base);
                if (committed && !has) {          // skipped letters
                    g.setColor(ERROR);
                    g.setStroke(new BasicStroke(2f));
                    g.drawLine(px + 2, base + 6, px + cw - 2, base + 6);
                }
            }
        }

        // input line with caret
        int inBase = y + h - 62;
        String in = input.toString();
        String target = wordIdx < words.size() ? words.get(wordIdx) : "";
        if (in.isEmpty() && (!started || finished)) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            g.setColor(MUTED);
            g.drawString(finished ? "Time's up!  Press TAB or click Restart for a new test."
                    : "Start typing to begin...  (TAB restarts)", left + 20, inBase - 4);
            g.setFont(font);
        }
        for (int j = 0; j < in.length(); j++) {
            boolean ok = j < target.length() && target.charAt(j) == in.charAt(j);
            g.setColor(ok ? INK : ERROR);
            g.drawString(String.valueOf(in.charAt(j)), left + j * cw, inBase);
        }
        if (!finished && (System.currentTimeMillis() / 500) % 2 == 0) {
            g.setColor(INK);
            g.setStroke(new BasicStroke(2.2f));
            int cxp = left + in.length() * cw;
            g.drawLine(cxp, inBase - fm.getAscent() + 2, cxp, inBase + 8);
        }
        g.setColor(CARD_BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.drawLine(left, y + h - 28, x + w - 35, y + h - 28);

        g.setClip(oldClip);
    }

    private void drawStats(Graphics2D g, int x, int y, int w, int h) {
        drawCard(g, x, y, w, h);
        String[] labels = {"AWPM", "WPM", "Accuracy", "Errors"};
        String[] values = {
                String.format("%.2f", awpm()),
                String.format("%.2f", wpm()),
                String.format("%.2f%%", accuracy()),
                String.valueOf(errorKeys)};
        double[] frac = {0.27, 0.22, 0.28, 0.23};
        Font lf = new Font(Font.SANS_SERIF, Font.PLAIN, 18);
        double cx = x;
        for (int i = 0; i < 4; i++) {
            int colW = (int) (w * frac[i]);
            int tx = (int) cx + 26;
            g.setFont(lf);
            g.setColor(LABEL);
            g.drawString(labels[i], tx, y + 46);
            g.setFont(new Font(Font.SANS_SERIF, i == 0 ? Font.BOLD : Font.PLAIN, i == 0 ? 46 : 38));
            g.setColor(INK);
            g.drawString(values[i], tx, y + 100);
            if (i > 0) {
                g.setColor(CARD_BORDER);
                g.setStroke(new BasicStroke(1.4f));
                g.drawLine((int) cx, y + 28, (int) cx, y + h - 28);
            }
            cx += colW;
        }
    }

    private void drawChart(Graphics2D g, int x, int y, int w, int h) {
        drawCard(g, x, y, w, h);
        double maxV = 120;
        for (double v : samples) maxV = Math.max(maxV, Math.ceil(v / 30.0) * 30);

        int x0 = x + 58, x1 = x + w - 22, y0 = y + 22, y1 = y + h - 30;
        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();

        int steps = (int) (maxV / 30);
        for (int i = 0; i <= steps; i++) {
            double v = i * 30;
            int gy = (int) (y1 - v / maxV * (y1 - y0));
            g.setColor(new Color(0xE3E7F1));
            g.setStroke(new BasicStroke(1f));
            g.drawLine(x0, gy, x1, gy);
            g.setColor(LABEL);
            String s = String.valueOf((int) v);
            g.drawString(s, x0 - 12 - fm.stringWidth(s), gy + fm.getAscent() / 2 - 1);
        }
        g.setColor(new Color(0xC9CFDF));
        g.drawLine(x0, y0, x0, y1);

        int n = samples.size();
        if (n == 0) return;
        double[] px = new double[n], py = new double[n];
        for (int i = 0; i < n; i++) {
            px[i] = x0 + (i + 1.0) / duration * (x1 - x0);
            py[i] = y1 - Math.min(samples.get(i), maxV) / maxV * (y1 - y0);
        }
        Path2D line = new Path2D.Double();
        line.moveTo(px[0], py[0]);
        for (int i = 1; i < n; i++) {
            double mx = (px[i - 1] + px[i]) / 2, my = (py[i - 1] + py[i]) / 2;
            line.quadTo(px[i - 1], py[i - 1], mx, my);
        }
        line.lineTo(px[n - 1], py[n - 1]);

        Path2D area = new Path2D.Double(line);
        area.lineTo(px[n - 1], y1);
        area.lineTo(px[0], y1);
        area.closePath();
        g.setPaint(new GradientPaint(0, y0, new Color(47, 107, 255, 70), 0, y1, new Color(47, 107, 255, 8)));
        g.fill(area);

        g.setPaint(ACCENT);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(line);

        int step = Math.max(1, n / 12);
        for (int i = 0; i < n; i += step) dot(g, px[i], py[i]);
        dot(g, px[n - 1], py[n - 1]);
    }

    private void dot(Graphics2D g, double cx, double cy) {
        g.setColor(ACCENT);
        g.fill(new Ellipse2D.Double(cx - 4, cy - 4, 8, 8));
    }

    private void drawRestart(Graphics2D g, int cx, int cy) {
        int bw = 200, bh = 52;
        restartBtn.setBounds(cx - bw / 2, cy - bh / 2, bw, bh);
        g.setColor(hoverRestart ? new Color(0xE9EEFB) : CARD);
        g.fillRoundRect(cx - bw / 2, cy - bh / 2, bw, bh, 16, 16);
        g.setColor(CARD_BORDER);
        g.setStroke(new BasicStroke(1.2f));
        g.drawRoundRect(cx - bw / 2, cy - bh / 2, bw, bh, 16, 16);

        // circular arrow icon
        int ix = cx - 52, r = 11;
        g.setColor(INK);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(ix - r, cy - r, 2 * r, 2 * r, 60, 280, Arc2D.OPEN));
        double ax = ix + r * Math.cos(Math.toRadians(60)), ay = cy - r * Math.sin(Math.toRadians(60));
        g.draw(new Line2D.Double(ax, ay, ax + 7, ay - 1));
        g.draw(new Line2D.Double(ax, ay, ax + 1, ay - 8));

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
        g.drawString("Restart", cx - 28, cy + 7);
    }

    // =====================================================================
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("AWPM Calculator");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            AWPMCalculator p = new AWPMCalculator();
            f.setContentPane(p);
            f.pack();
            f.setMinimumSize(new Dimension(900, 640));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            p.requestFocusInWindow();
        });
    }
}
