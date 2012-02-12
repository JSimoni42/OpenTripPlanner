/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (props, at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.algorithm.strategies;

import java.util.HashMap;

import org.opentripplanner.common.model.T2;
import org.opentripplanner.routing.core.SimplifiedLowerBoundGraph;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseOptions;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.location.StreetLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Intended primarily for testing of and experimentation with heuristics based on the triangle
 * inequality and metric embeddings.
 * 
 * A heuristic that performs a single-source / all destinations shortest path search in a weighted,
 * directed graph whose shortest path metric is a lower bound on path weight in our main,
 * time-dependent graph.
 * 
 * @author andrewbyrd
 */
public class SLBGRemainingWeightHeuristic implements RemainingWeightHeuristic {

    private static final long serialVersionUID = 20110901L;

    private static Logger LOG = LoggerFactory.getLogger(SLBGRemainingWeightHeuristic.class);

    private static HashMap<GraphAndDirection, SimplifiedLowerBoundGraph> 
        lbgCache = new HashMap<GraphAndDirection, SimplifiedLowerBoundGraph>();

    SimplifiedLowerBoundGraph lbg;

    Vertex target;

    double[] weights;

    public SLBGRemainingWeightHeuristic(Graph g, TraverseOptions opt) {
        GraphAndDirection key = new GraphAndDirection(g, opt.isArriveBy());
        this.lbg = lbgCache.get(key);
        if (this.lbg == null) {
            LOG.debug("no lower bound graph found for: {}", key);
            LOG.debug("BEGIN Making lower bound graph");
            if (opt.isArriveBy())
                this.lbg = new SimplifiedLowerBoundGraph(g); //, LowerBoundGraph.OUTGOING);
            else 
                this.lbg = new SimplifiedLowerBoundGraph(g); //, LowerBoundGraph.INCOMING);
            LOG.debug("END Making lower bound graph");
            lbgCache.put(key, this.lbg);
        } else {
            LOG.debug("reusing cached lower bound graph found for: {}", key);
        }
    }

    @Override
    public double computeInitialWeight(State s, Vertex target) {
        recalculate(target, s.getOptions());
        return 0;
    }

    @Override
    public double computeForwardWeight(State s, Vertex target) {
        return computeReverseWeight(s, target);
    }

    @Override
    public double computeReverseWeight(State s, Vertex target) {
        int group = s.getVertex().getGroupIndex();
        if (group < weights.length && group >= 0) {
            double h = weights[group];
            //System.out.printf("h=%f at %s\n", h, s.getVertex());
            return h == Double.POSITIVE_INFINITY ? 0 : h;
        } else
            return 0;
    }

    private void recalculate(Vertex target, TraverseOptions options) {
        if (target != this.target) {
            this.target = target;
            if (target instanceof StreetLocation)
                this.weights = lbg.sssp((StreetLocation) target, options);
            else // wrong FIXME 
                this.weights = lbg.sssp((StreetLocation) target, options);
        }
    }

    @Override
    public void reset() {
    }
    
    private static class GraphAndDirection extends T2<Graph, Boolean> {
        private static final long serialVersionUID = 20110901L;
        public GraphAndDirection(Graph g, Boolean i) {
            super(g, i);
        }
    }
    
}
