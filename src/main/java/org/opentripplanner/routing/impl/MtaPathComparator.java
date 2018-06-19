/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.impl;

import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.spt.GraphPath;

public class MtaPathComparator extends PathComparator {

    private boolean specialOrder;

    public MtaPathComparator(boolean compareStartTimes, boolean specialOrder) {
        super(compareStartTimes);
        this.specialOrder = specialOrder;
    }

    // Walking trips should appear last in results
    @Override
    public int compare(GraphPath o1, GraphPath o2) {
        boolean o1NoTransit = o1.getTrips().isEmpty();
        boolean o2NoTransit = o2.getTrips().isEmpty();
        if (o1NoTransit && !o2NoTransit)
            return 1;
        if (!o1NoTransit && o2NoTransit)
            return -1;
        if (specialOrder) {
            if (o2.getDuration() < o1.getDuration() && o2.getEndTime() < o1.getEndTime() && o2.getTrips().size() <= o1.getTrips().size())
                return 1;
            if (o1.getDuration() < o2.getDuration() && o1.getEndTime() < o2.getEndTime() && o1.getTrips().size() <= o2.getTrips().size())
                return -1;
        }
        return weight(o1) - weight(o2) > 0 ? 1 : -1;
    }

    private double weight(GraphPath path) {
        RoutingRequest options = path.states.iterator().next().getOptions();
        long startTime = path.getStartTime();
        long endTime = path.getEndTime();
        long waitTime;
        if (compareStartTimes) {
            // arriveBy = true (reverse search)
            waitTime = options.dateTime - endTime;
        } else {
            waitTime = startTime - options.dateTime;
        }
        return (waitTime * options.waitAtBeginningFactor) + path.getWeight();
    }

}
