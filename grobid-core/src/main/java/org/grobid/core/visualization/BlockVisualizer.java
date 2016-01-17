package org.grobid.core.visualization;

import com.google.common.base.Predicate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.grobid.core.document.Document;
import org.grobid.core.document.DocumentSource;
import org.grobid.core.engines.Engine;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.factory.GrobidFactory;
import org.grobid.core.layout.Block;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.main.LibraryLoader;
import org.grobid.core.utilities.ElementCounter;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.XQueryProcessor;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by zholudev on 15/01/16.
 * Visualizing blocks
 */
public class BlockVisualizer {

    public static void main(String[] args) {
        try {
            File input = new File("/Work/temp/context/coords/2.pdf");
//            File input = new File("/Work/temp/figureExtraction/3.pdf");

            final PDDocument document = PDDocument.load(input);
            File outPdf = new File("/tmp/test.pdf");

            GrobidProperties.set_GROBID_HOME_PATH("grobid-home");
            GrobidProperties.setGrobidPropertiesPath("grobid-home/config/grobid.properties");
            LibraryLoader.load();
            final Engine engine = GrobidFactory.getInstance().getEngine();
            GrobidAnalysisConfig config = new GrobidAnalysisConfig.GrobidAnalysisConfigBuilder()
                    .pdfAssetPath(new File("/tmp/x"))
                    .build();

//            File tempFile = File.createTempFile("temp", ".xml", new File("/tmp"));
            DocumentSource documentSource = DocumentSource.fromPdf(input);


            Document teiDoc = engine.fullTextToTEIDoc(input, config);

            PDDocument out = annotateBlocks(document, documentSource.getXmlFile(), teiDoc);
//            PDDocument out = annotateBlocks(document, documentSource.getXmlFile(), null);

            if (out != null) {
                out.save(outPdf);
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(outPdf);
                }
            }
            System.out.println(Engine.getCntManager());

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }

    }

    public static PDDocument annotateBlocks(PDDocument document, File xmlFile, Document teiDoc) throws IOException, XPathException {

        Multimap<Integer, Block> blockMultimap = HashMultimap.create();

        ElementCounter<Integer> leftEven = new ElementCounter<>();
        ElementCounter<Integer> rightEven = new ElementCounter<>();
        ElementCounter<Integer> leftOdd = new ElementCounter<>();
        ElementCounter<Integer> rightOdd = new ElementCounter<>();
        ElementCounter<Integer> top = new ElementCounter<>();
        ElementCounter<Integer> bottom = new ElementCounter<>();


        if (teiDoc != null) {
            for (Block b : teiDoc.getBlocks()) {
//                AnnotationUtil.annotatePage(document, b.getPageNumber() + "," + b.getX() + "," + b.getY() +
//                        "," + b.getWidth() + "," + b.getHeight(), 0);
                blockMultimap.put(b.getPageNumber(), b);

                if (b.getX() == 0 || b.getHeight() < 20) {

                    continue;
                }

                if (b.getPageNumber() % 2 == 0) {
                    leftEven.i((int) b.getX());
                    rightEven.i((int) (b.getX() + b.getWidth()));
                } else {
                    leftOdd.i((int) b.getX());
                    rightOdd.i((int) (b.getX() + b.getWidth()));
                }

                top.i((int) b.getY());
                bottom.i((int) (b.getY() + b.getHeight()));
            }
        }

        int evenX = getCoord(leftEven, true);
        int oddX = getCoord(leftOdd, true);
        int evenWidth = getCoord(rightEven, false) - evenX;
        int oddWidth = getCoord(rightOdd, false) - oddX;
        int Y = getCoord(top, true);
        int height = getCoord(bottom, false) - Y;


        for (int pageNum = 1; pageNum <= document.getNumberOfPages(); pageNum++) {
            if (pageNum % 2 == 0) {
                AnnotationUtil.annotatePage(document,
                        AnnotationUtil.getCoordString(pageNum, evenX, Y, evenWidth, height), 10);
            } else {
                AnnotationUtil.annotatePage(document,
                        AnnotationUtil.getCoordString(pageNum, oddX, Y, oddWidth, height), 10);

            }
            String q = "\n" +
                    "for $g  in //GROUP return\n" +
                    "\n" +
                    "  let $x1 := min(($g//*/@x, $g//*/@x1, $g//*/@x2, $g//*/@x3))\n" +
                    "  let $y1 := min(($g/*/@y, $g//*/@y1, $g//*/@y2, $g//*/@y3))\n" +
                    "  let $x2 := max(($g/*/@x, $g//*/@x1, $g//*/@x2, $g//*/@x3))\n" +
                    "  let $y2 := max(($g/*/@y, $g//*/@y1, $g//*/@y2, $g//*/@y3))\n" +
                    "  return concat($x1, \",\", $y1, \",\", $x2 - $x1, \",\", $y2 - $y1)";
//            XQueryProcessor pr = new XQueryProcessor(new File("/Work/temp/figureExtraction/xmldata_2/image-" + pageNum + ".vec"));
            XQueryProcessor pr = new XQueryProcessor(new File(xmlFile.getAbsolutePath() + "_data", "image-" + pageNum + ".vec"));
            SequenceIterator it = pr.getSequenceIterator(q);
            Item item;
            List<BoundingBox> boxes = new ArrayList<>();

            while ((item = it.next()) != null) {
                String c = item.getStringValue();
                String coords = pageNum + "," + c;
                BoundingBox e = BoundingBox.fromString(coords);
                //TODO: detect borders
                if (
//                        e.getX() == 0 && e.getY() == 0
//                        ||
                                (e.getX() < evenX && e.getPage() % 2 == 0 || e.getX() < oddX && e.getPage() % 2 != 0)
                        || e.getY() < Y || e.getY() + e.getHeight() > Y + height
                        ) {
                    continue;
                }
                boxes.add(e);
//                AnnotationUtil.annotatePage(document, coords, 1);
            }

            List<BoundingBox> remainingBoxes = mergeBoxes(boxes);

            for (int i = 0; i < remainingBoxes.size(); i++) {
                Collection<Block> col = blockMultimap.get(pageNum);
                for (Block bl : col) {
                    BoundingBox b = BoundingBox.fromPointAndDimensions(pageNum, bl.getX(), bl.getY(), bl.getWidth(), bl.getHeight());
                    if (remainingBoxes.get(i).intersect(b)) {
                        remainingBoxes.set(i, remainingBoxes.get(i).boundBox(b));
                    }
                }
            }

            remainingBoxes = mergeBoxes(remainingBoxes);

            for (BoundingBox b : remainingBoxes) {
                AnnotationUtil.annotatePage(document, b.toString(), 1);
            }
        }


        return document;
    }

    private static int getCoord(ElementCounter<Integer> cnt, boolean getMin) {

        List<Map.Entry<Integer, Integer>> counts = cnt.getSortedCounts();
        int max = counts.get(0).getValue();

        int res = counts.get(0).getKey();
        for (Map.Entry<Integer, Integer> e : counts) {
            if (e.getValue() < max * 0.7) {
                break;
            }

            if (getMin) {
                if (e.getKey() < res) {
                    res = e.getKey();
                }
            } else {
                if (e.getKey() > res) {
                    res = e.getKey();
                }
            }
        }
        return res;
    }

    private static List<BoundingBox> mergeBoxes(List<BoundingBox> boxes) {
        boolean allMerged = false;
        while (!allMerged) {
            allMerged = true;
            for (int i = 0; i < boxes.size(); i++) {
                BoundingBox a = boxes.get(i);
                if (a == null) continue;
                for (int j = i + 1; j < boxes.size(); j++) {
                    BoundingBox b = boxes.get(j);
                    if (b != null) {
                        if (a.intersect(b)) {
                            allMerged = false;
                            a = a.boundBox(b);
                            boxes.set(i, a);
                            boxes.set(j, null);
                        }
                    }
                }
            }
        }

        return Lists.newArrayList(Iterables.filter(boxes, new Predicate<BoundingBox>() {
            @Override
            public boolean apply(BoundingBox boundingBox) {
                if (boundingBox == null) {
                    return false;
                }
                if (boundingBox.getHeight() < 5 || boundingBox.getWidth() < 5) {
                    return false;
                }
                return true;
            }
        }));
    }
}