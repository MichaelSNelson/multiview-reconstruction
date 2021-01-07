/*-
 * #%L
 * Software for the reconstruction of multi-view microscopic acquisitions
 * like Selective Plane Illumination Microscopy (SPIM) Data.
 * %%
 * Copyright (C) 2012 - 2021 Multiview Reconstruction developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package net.preibisch.mvrecon.process.interestpointregistration.pairwise.methods.centerofmass;

/**
 * Center of Mass (Avg/Median) implementation
 * 
 * @author Stephan Preibisch (stephan.preibisch@gmx.de)
 *
 */
public class CenterOfMassParameters
{
	final static String[] centerChoice = new String[]{ "Average", "Median" };
	public static int defaultCenterChoice = 0;
	protected int centerType = 0;

	public CenterOfMassParameters( final int centerType ) { this.centerType = centerType; }

	public int getCenterType() { return centerType; }
}
