package org.opentripplanner.graph_builder.module.ned;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.strtree.STRtree;
import org.geotools.coverage.AbstractCoverage;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.coverage.CannotEvaluateException;
import org.opengis.coverage.Coverage;
import org.opengis.coverage.PointOutsideCoverageException;
import org.opengis.coverage.SampleDimension;
import org.opengis.geometry.DirectPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Stitches together multiple elevation maps into a single elevation map,
 * hackily.  This is horrible, but the geotools way of doing things is
 * too slow.   
 * @author novalis
 *
 */
public class UnifiedGridCoverage extends AbstractCoverage {

    private static final long serialVersionUID = -7798801307087575896L;

    private static Logger log = LoggerFactory.getLogger(UnifiedGridCoverage.class);
    private final SpatialIndex datumRegionIndex;
    private ArrayList<Coverage> regions;
    private List<VerticalDatum> datums;

    /**
     * It would be nice if we could construct this unified coverage with zero sub-coverages and add all sub-coverages
     * in the same way. However, the superclass constructor (AbstractCoverage) needs a coverage to copy properties from.
     * So the first sub-coverage needs to be passed in at construction time.
     */
    protected UnifiedGridCoverage(CharSequence name, GridCoverage2D coverage, List<VerticalDatum> datums) {
        super(name, coverage);
        regions = new ArrayList<>();
        this.datums = datums;
        datumRegionIndex = new STRtree();
        // Add first coverage to list of regions/spatial index.
        this.add(coverage);
    }

    @Override
    public Object evaluate(DirectPosition point) throws CannotEvaluateException {
        /* we don't use this function, we use evaluate(DirectPosition point, double[] values) */
        return null;
    }

    public double[] evaluate(DirectPosition point, double[] values) throws CannotEvaluateException {
        double x = point.getOrdinate(0);
        double y = point.getOrdinate(1);
        Coordinate pointCoordinate = new Coordinate(x, y);
        Envelope envelope = new Envelope(pointCoordinate);
        List<DatumRegion> coverageCandidates = datumRegionIndex.query(envelope);
        if (coverageCandidates.size() > 0) {
            // Found a match for coverage/datum.
            DatumRegion datumRegion = coverageCandidates.get(0);
            double[] result;
            try {
                result = datumRegion.region.evaluate(point, values);
                result[0] += datumRegion.datum.interpolatedHeight(x, y);
                return result;
            } catch (PointOutsideCoverageException e) {
                /* not found */
                log.warn("Point not found: " + point);
                return null;
            }
        }
        /* not found */
        log.warn("Point not found: " + point);
        return null;
    }
    
    @Override
    public int getNumSampleDimensions() {
        return regions.get(0).getNumSampleDimensions();
    }

    @Override
    public SampleDimension getSampleDimension(int index) throws IndexOutOfBoundsException {
        return regions.get(0).getSampleDimension(index);
    }

    public void add(GridCoverage2D regionCoverage) {
        // Iterate over datums to find intersection envelope with each region and add to spatial index.
        for (VerticalDatum datum : datums) {
            Envelope datumEnvelope = new Envelope(datum.lowerLeftLongitude, datum.lowerLeftLongitude + datum.deltaLongitude, datum.lowerLeftLatitude, datum.lowerLeftLatitude + datum.deltaLatitude);
            ReferencedEnvelope regionEnvelope = new ReferencedEnvelope(regionCoverage.getEnvelope());
            Envelope intersection = regionEnvelope.intersection(datumEnvelope);
            datumRegionIndex.insert(intersection, new DatumRegion(datum, regionCoverage));
        }
        regions.add(regionCoverage);
    }

    public class DatumRegion {
        public final VerticalDatum datum;
        public final Coverage region;

        public DatumRegion (VerticalDatum datum, Coverage region) {
            this.datum = datum;
            this.region = region;
        }
    }

}
