/*
 * Copyright 2018-2019 Gerrit Meinders
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package helium;

import java.io.*;
import javax.swing.*;

import helium.swing.*;

/**
 * This class initiates loading of configuration settings and creation of the
 * user interface.
 *
 * @author Gerrit Meinders
 */
public class RecorderStandalone
	implements Runnable
{
	/**
	 * Starts the application from the AWT event thread with the given
	 * arguments.
	 *
	 * @param args the command-line arguments
	 */
	public static void main( final String[] args )
	{
		SwingUtilities.invokeLater( new RecorderStandalone() );
	}

	/**
	 * Starts the application.
	 */
	@Override
	public void run()
	{
		// configure look and feel
		try
		{
			UIManager.put( "swing.boldMetal", Boolean.FALSE );
			// UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
		}
		catch ( final Exception e )
		{
			// The cross-platform LAF is always available.
		}

		final ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
		toolTipManager.setLightWeightPopupEnabled( false );
		JFrame.setDefaultLookAndFeelDecorated( true );
		JDialog.setDefaultLookAndFeelDecorated( true );

		try
		{
			final Config config = new Config();
			config.load();

			final RecordingUI ui = new RecordingUI( config );
			ui.setVisible( true );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
	}
}
