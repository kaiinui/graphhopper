/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.util;

import java.util.List;

public class Instruction
{
    private static final AngleCalc ac = new AngleCalc();
    private static final DistanceCalc3D distanceCalc = new DistanceCalc3D();
    public static final int TURN_SHARP_LEFT = -3;
    public static final int TURN_LEFT = -2;
    public static final int TURN_SLIGHT_LEFT = -1;
    public static final int CONTINUE_ON_STREET = 0;
    public static final int TURN_SLIGHT_RIGHT = 1;
    public static final int TURN_RIGHT = 2;
    public static final int TURN_SHARP_RIGHT = 3;
    public static final int FINISH = 4;
    public static final int REACHED_VIA = 5;
    protected int sign;
    private final String name;
    private double distance;
    private long time;
    final PointList points;
    private final InstructionAnnotation annotation;

    /**
     * The points, distances and times have exactly the same count. The last point of this
     * instruction is not duplicated here and should be in the next one.
     */
    public Instruction( int sign, String name, InstructionAnnotation ia, PointList pl )
    {
        this.sign = sign;
        this.name = name;
        this.points = pl;
        this.annotation = ia;
    }

    public InstructionAnnotation getAnnotation()
    {
        return annotation;
    }

    public int getSign()
    {
        return sign;
    }

    /**
     * The instruction for the person/driver to execute.
     */
    public String getName()
    {
        return name;
    }

    public Instruction setDistance( double distance )
    {
        this.distance = distance;
        return this;
    }

    /**
     * Distance in meter until no new instruction
     */
    public double getDistance()
    {
        return distance;
    }

    public Instruction setTime( long time )
    {
        this.time = time;
        return this;
    }

    /**
     * Time in time until no new instruction
     */
    public long getTime()
    {
        return time;
    }

    /**
     * Latitude of the location where this instruction should take place.
     */
    double getFirstLat()
    {
        return points.getLatitude(0);
    }

    /**
     * Longitude of the location where this instruction should take place.
     */
    double getFirstLon()
    {
        return points.getLongitude(0);
    }

    double getFirstEle()
    {
        return points.getElevation(0);
    }

    public PointList getPoints()
    {
        return points;
    }

    /**
     * This method returns a list of gpx entries where the time (in time) is relative to the first
     * which is 0. It does NOT contain the last point which is the first of the next instruction.
     * <p>
     * @return the time offset to add for the next instruction
     */
    long fillGPXList( List<GPXEntry> list, long time,
            Instruction prevInstr, Instruction nextInstr, boolean firstInstr )
    {
        checkOne();
        int len = points.size();
        long prevTime = time;
        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        double ele = Double.NaN;
        boolean is3D = points.is3D();
        if (is3D)
            ele = points.getElevation(0);

        for (int i = 0; i < len; i++)
        {
            list.add(new GPXEntry(lat, lon, ele, prevTime));
            
            boolean last = i + 1 == len;
            double nextLat = last ? nextInstr.getFirstLat() : points.getLatitude(i + 1);
            double nextLon = last ? nextInstr.getFirstLon() : points.getLongitude(i + 1);
            double nextEle = is3D ? (last ? nextInstr.getFirstEle() : points.getElevation(i + 1)) : Double.NaN;
            if (is3D)
                prevTime = Math.round(prevTime + this.time * distanceCalc.calcDist(nextLat, nextLon, nextEle, lat, lon, ele) / distance);
            else
                prevTime = Math.round(prevTime + this.time * distanceCalc.calcDist(nextLat, nextLon, lat, lon) / distance);

            lat = nextLat;
            lon = nextLon;
            ele = nextEle;
        }
        return time + this.time;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(sign).append(',');
        sb.append(name).append(',');
        sb.append(distance).append(',');
        sb.append(time);
        sb.append(')');
        return sb.toString();
    }

    /**
     * Return Direction/Compass point based on the first tracksegment of the instruction. If
     * Instruction does not contain enough coordinate points, an empty string will be returned.
     * <p>
     * @return
     */
    String getDirection( Instruction nextI )
    {
        double azimuth = calcAzimuth(nextI);
        if (Double.isNaN(azimuth))
            return "";

        return ac.azimuth2compassPoint(azimuth);
    }

    /**
     * Return Azimuth based on the first tracksegment of the instruction. If Instruction does not
     * contain enough coordinate points, an empty string will be returned.
     */
    String getAzimuth( Instruction nextI )
    {
        double az = calcAzimuth(nextI);
        if (Double.isNaN(az))
            return "";

        return "" + Math.round(az);
    }

    private double calcAzimuth( Instruction nextI )
    {
        double nextLat;
        double nextLon;

        if (points.getSize() >= 2)
        {
            nextLat = points.getLatitude(1);
            nextLon = points.getLongitude(1);
        } else if (points.getSize() == 1 && null != nextI)
        {
            nextLat = nextI.points.getLatitude(0);
            nextLon = nextI.points.getLongitude(0);
        } else
        {
            return Double.NaN;
        }

        double lat = points.getLatitude(0);
        double lon = points.getLongitude(0);
        return ac.calcAzimuth(lat, lon, nextLat, nextLon);
    }

    void checkOne()
    {
        if (points.size() < 1)
            throw new IllegalStateException("Instruction must contain at least one point " + toString());
    }

    public String getTurnDescription( Translation tr )
    {
        String str;
        String n = getName();
        int indi = getSign();
        if (indi == Instruction.FINISH)
        {
            str = tr.tr("finish");
        } else if (indi == Instruction.REACHED_VIA)
        {
            str = tr.tr("stopover", ((FinishInstruction) this).getViaPosition());
        } else if (indi == Instruction.CONTINUE_ON_STREET)
        {
            str = Helper.isEmpty(n) ? tr.tr("continue") : tr.tr("continue_onto", n);
        } else
        {
            String dir = null;
            switch (indi)
            {
                case Instruction.TURN_SHARP_LEFT:
                    dir = tr.tr("sharp_left");
                    break;
                case Instruction.TURN_LEFT:
                    dir = tr.tr("left");
                    break;
                case Instruction.TURN_SLIGHT_LEFT:
                    dir = tr.tr("slight_left");
                    break;
                case Instruction.TURN_SLIGHT_RIGHT:
                    dir = tr.tr("slight_right");
                    break;
                case Instruction.TURN_RIGHT:
                    dir = tr.tr("right");
                    break;
                case Instruction.TURN_SHARP_RIGHT:
                    dir = tr.tr("sharp_right");
                    break;
            }
            if (dir == null)
                throw new IllegalStateException("Indication not found " + indi);

            str = Helper.isEmpty(n) ? tr.tr("turn", dir) : tr.tr("turn_onto", dir, n);
        }
        return str;
    }
}
