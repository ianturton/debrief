/*
 *    Debrief - the Open Source Maritime Analysis Application
 *    http://debrief.info
 *
 *    (C) 2000-2014, PlanetMayo Ltd
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the Eclipse Public License v1.0
 *    (http://www.eclipse.org/legal/epl-v10.html)
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 */
package org.mwc.cmap.geotools.gt2plot;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;

import org.geotools.map.MapContent;
import org.geotools.swt.event.MapPaneListener;
import org.opengis.geometry.Envelope;

/** isolate the interface that the SwtMapPane provides which goes beyond a plain Canvas control
 * 
 * @author ian
 *
 */
public interface IDebriefMapPane
{
	public MapContent getMapContent();

	public void setDisplayArea(Envelope env);

	public RenderedImage getBaseImage();

	public Rectangle getBounds();

	public void addMapPaneListener(MapPaneListener mapPaneListener);

	/** provide easy access to the screen -> world transform
	 * 
	 * @return
	 */
	public AffineTransform getScreenToWorldTransform();
	
	/** provide easy access to the world -> screen transform
	 * 
	 * @return
	 */
	public AffineTransform getWorldToScreenTransform();


}
