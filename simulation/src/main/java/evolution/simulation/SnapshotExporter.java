package evolution.simulation;

import evolution.game.Genotype;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.io.IOException;
import java.util.Locale;

/** Headless frame export shared by research illustrations and the selected JavaFX snapshot. */
public final class SnapshotExporter {
    private SnapshotExporter() {}
    public static String key(Genotype g) { return ""+g.firstMove()+g.afterCC()+g.afterCD()+g.afterDC()+g.afterDD(); }
    public static Color color(String g) {
        int i=Integer.parseInt(g.replace('C','0').replace('D','1'),2);
        return Color.getHSBColor((float)((i*137.508%360)/360),.70f,i%2==0?.85f:.65f);
    }
    public static void write(Path png,SimulationSnapshot s,String scenario) throws IOException {
        if(Files.exists(png) || Files.exists(Path.of(png+".svg"))) throw new IOException("Frame already exists: "+png);
        if(png.toAbsolutePath().getParent()!=null) Files.createDirectories(png.toAbsolutePath().getParent());
        int width=1000,height=840;double cell=680.0/s.sideLength();int ox=20,oy=100;
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);g.fillRect(0,0,width,height);g.setFont(new Font("SansSerif",Font.BOLD,20));g.setColor(new Color(0x183447));
        String title=scenario+" | seed="+s.seed()+" | generation="+s.generation()+" tick="+s.tick();
        g.drawString(title,20,32);g.setFont(new Font("SansSerif",Font.PLAIN,14));
        g.drawString("genetic-v2-controls | torus | agents="+s.agents().size()+" | resources="+s.resources().size(),20,60);
        g.drawString("Circles: genotype; gold squares: resource (also on occupied cells)",20,82);
        var svg=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1000\" height=\"840\" viewBox=\"0 0 1000 840\"><rect width=\"1000\" height=\"840\" fill=\"white\"/>");
        svg.append("<g font-family=\"sans-serif\" fill=\"#183447\"><text x=\"20\" y=\"32\" font-size=\"20\">").append(escape(title)).append("</text><text x=\"20\" y=\"60\">Circles: genotype; gold squares: resource; toroidal world</text></g>");
        g.setColor(new Color(0xd5dfe8));
        for(int i=0;i<=s.sideLength();i++) {
            int x=ox+(int)(i*cell),y=oy+(int)(i*cell);g.drawLine(x,oy,x,oy+680);g.drawLine(ox,y,ox+680,y);
            svg.append(String.format(Locale.ROOT,"<path d=\"M %d 100 v 680 M 20 %d h 680\" stroke=\"#d5dfe8\" stroke-width=\".6\"/>",x,y));
        }
        for(var a:s.agents()) {
            double x=ox+(a.position().column()+.19)*cell,y=oy+(a.position().row()+.19)*cell;
            Color c=color(key(a.genotype()));g.setColor(c);g.fillOval((int)x,(int)y,(int)(cell*.62),(int)(cell*.62));
            svg.append(String.format(Locale.ROOT,"<circle cx=\"%.2f\" cy=\"%.2f\" r=\"%.2f\" fill=\"#%06x\"/>",x+cell*.31,y+cell*.31,cell*.31,c.getRGB()&0xffffff));
        }
        for(var r:s.resources()) {
            double x=ox+(r.column()+.395)*cell,y=oy+(r.row()+.395)*cell;
            g.setColor(new Color(0xefb72c));g.fillRect((int)x,(int)y,(int)(cell*.21)+1,(int)(cell*.21)+1);
            g.setColor(new Color(0x805a00));g.drawRect((int)x,(int)y,(int)(cell*.21)+1,(int)(cell*.21)+1);
            svg.append(String.format(Locale.ROOT,"<rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" fill=\"#efb72c\" stroke=\"#805a00\"/>",x,y,cell*.21,cell*.21));
        }
        for(int i=0;i<32;i++) {
            String gt=String.format(Locale.ROOT,"%5s",Integer.toBinaryString(i)).replace(' ','0').replace('0','C').replace('1','D');
            int x=735,y=105+i*21;g.setColor(color(gt));g.fillOval(x,y-10,12,12);g.setColor(Color.DARK_GRAY);g.drawString(gt,x+20,y);
            svg.append(String.format(Locale.ROOT,"<circle cx=\"741\" cy=\"%d\" r=\"6\" fill=\"#%06x\"/><text x=\"755\" y=\"%d\" font-family=\"monospace\" font-size=\"14\">%s</text>",y-4,color(gt).getRGB()&0xffffff,y,gt));
        }
        g.drawString("Illustrative run; not a population-level estimate. Elapsed ticks: "+s.elapsedTicks(),20,812);g.dispose();
        ImageIO.write(image,"png",png.toFile());Files.writeString(Path.of(png+".svg"),svg.append("</svg>").toString());
    }
    private static String escape(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace("\"","&quot;"); }
}
