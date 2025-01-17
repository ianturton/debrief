package org.mwc.debrief.lite.map;

import java.awt.event.ActionEvent;

import javax.swing.JLabel;

import org.geotools.swing.MapPane;
import org.geotools.swing.action.MapAction;
import org.geotools.swing.tool.ZoomInTool;

/**
 * An action for connect a control (probably a JButton) to
 * the Range Bearing tool - for measuring range and bearing
 * 
 * @author Ian Mayo, from Michael Bedward's Zoom In Action
 * @since 2.6
 */
public class RangeBearingAction extends MapAction {

    /**
   * 
   */
  private static final long serialVersionUID = 1L;
    private final JLabel _statusBar;

    /**
     * Constructor. The associated control will be labelled with an icon.
     * 
     * @param mapPane the map pane being serviced by this action
     * @param statusBar 
     */
    public RangeBearingAction(MapPane mapPane, JLabel statusBar) {
        this(mapPane, false, statusBar);
    }

    /**
     * Constructor. The associated control will be labelled with an icon and,
     * optionally, the tool name.
     * 
     * @param mapPane the map pane being serviced by this action
     * @param showToolName set to true for the control to display the tool name
     */
    public RangeBearingAction(MapPane mapPane, boolean showToolName, JLabel statusBar) {
        String toolName = showToolName ? ZoomInTool.TOOL_NAME : null;
        super.init(mapPane, toolName, ZoomInTool.TOOL_TIP, ZoomInTool.ICON_IMAGE);
        _statusBar = statusBar;
    }
    
    /**
     * Called when the associated control is activated. Leads to the
     * map pane's cursor tool being set to a new ZoomInTool object
     *
     * @param ev the event (not used)
     */
    public void actionPerformed(ActionEvent ev) {
        getMapPane().setCursorTool(new RangeBearingTool(_statusBar));
    }

}
