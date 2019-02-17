/*
 * Copyright 2019 Gerrit Meinders
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

package helium.swing;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import javax.swing.*;

import helium.*;
import org.jetbrains.annotations.*;

/**
 * User interface used to control and monitor the recording.
 *
 * @author Gerrit Meinders
 */
public class RecordingUI
	extends JFrame
{
	private final Config _config;

	private Recorder recorder;

	public RecordingUI( final Config config )
	{
		super( ResourceBundle.getBundle( "helium" ).getString( "title" ) );

		_config = config;
		recorder = new Recorder( _config );

		setDefaultCloseOperation( DISPOSE_ON_CLOSE );
		setContentPane( createContentPane() );
		addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				try
				{
					recorder.stop();
				}
				catch ( final IOException ex )
				{
					showExceptionDialog( ResourceBundle.getBundle( "helium" ).getString(
						"exception.stop" ), ex );
				}
			}
		} );

		pack();
		final GraphicsConfiguration graphicsConfiguration = getGraphicsConfiguration();
		final Rectangle bounds = graphicsConfiguration.getBounds();
		setLocation( (int)bounds.getCenterX() - getWidth() / 2,
		             (int)bounds.getCenterY() - getHeight() / 2 );
	}

	private Recorder getRecorder()
	{
		return recorder;
	}

	private Container createContentPane()
	{
		final JVolumeBar volumeBar = new JVolumeBar();
		volumeBar.setOrientation( JVolumeBar.Orientation.HORIZONTAL );
		volumeBar.setAlignmentX( CENTER_ALIGNMENT );
		volumeBar.setBorder( BorderFactory.createEmptyBorder( 0, 5, 0, 5 ) );

		final JLabel timeLabel = new JLabel( "-:--'--\"" );
		timeLabel.setAlignmentX( CENTER_ALIGNMENT );
		timeLabel.setBorder( BorderFactory.createEmptyBorder() );
		final int fontSize = 8 * getToolkit().getScreenResolution() / 72;
		timeLabel.setFont( new Font( Font.MONOSPACED, Font.BOLD, fontSize ) );

		class Listener
			implements AmplitudeListener, GainListener
		{
			private DecimalFormat twoDigitFormat = new DecimalFormat( "00" );

			private long startTimeMillis;

			@Override
			public void amplitudeChanged( final int channel, final double amplitude )
			{
				if ( recorder.isStarted() )
				{
					volumeBar.setAmplitude( channel, amplitude );

					int seconds = (int)( ( System.currentTimeMillis() - startTimeMillis ) / 1000 );
					int minutes = seconds / 60;
					final int hours = minutes / 60;
					minutes %= 60;
					seconds %= 60;
					timeLabel.setText( hours + ":"
					                   + twoDigitFormat.format( minutes ) + "'"
					                   + twoDigitFormat.format( seconds ) + '"' );
				}
			}

			@Override
			public void gainChanged( final int channel, final double gain )
			{
				volumeBar.setGain( channel, gain );
			}
		}

		final Listener listener = new Listener();
		recorder.addAmplitudeListener( listener );
		recorder.addGainListener( listener );

		final Action[] recordStopActions = new Action[ 2 ];

		final Action recordAction = new AbstractAction()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				setEnabled( false );
				recorder.setConfig( _config );
				try
				{
					recorder.start();

					recordStopActions[ 1 ].setEnabled( true );
					listener.startTimeMillis = System.currentTimeMillis();

				}
				catch ( final Exception ex )
				{
					try
					{
						recorder.stop();
					}
					catch ( final IOException e1 )
					{
						e1.printStackTrace();
					}
					showExceptionDialog( ResourceBundle.getBundle( "helium" ).getString(
						"exception.record" ), ex );
					setEnabled( true );
				}
			}
		};
		recordAction.putValue( Action.LARGE_ICON_KEY, getIcon( "icons/Record24.gif" ) );
		recordStopActions[ 0 ] = recordAction;

		final Action stopAction = new AbstractAction()
		{
			@Override
			public void actionPerformed( final ActionEvent e )
			{
				setEnabled( false );
				try
				{
					recorder.stop();

					volumeBar.clear();
					timeLabel.setText( "-:--'--\"" );
					recordStopActions[ 0 ].setEnabled( true );

				}
				catch ( final Exception ex )
				{
					showExceptionDialog( ResourceBundle.getBundle( "helium" ).getString(
						"exception.stop" ), ex );
					setEnabled( true );
				}
			}
		};
		stopAction.putValue( Action.LARGE_ICON_KEY, getIcon( "icons/Stop24.gif" ) );
		stopAction.setEnabled( false );
		recordStopActions[ 1 ] = stopAction;

		final JToolBar toolBar = new JToolBar();
		toolBar.setAlignmentX( LEFT_ALIGNMENT );
		toolBar.setFloatable( false );
		toolBar.setOrientation( SwingConstants.HORIZONTAL );
		toolBar.add( Box.createHorizontalGlue() );
		toolBar.add( timeLabel );
		toolBar.add( Box.createHorizontalGlue() );
		toolBar.add( recordAction );
		toolBar.add( stopAction );
		toolBar.setBorder( BorderFactory.createEmptyBorder() );

		final JPanel centerPanel = new JPanel();
		centerPanel.setLayout( new BoxLayout( centerPanel, BoxLayout.LINE_AXIS ) );
		centerPanel.setBorder( BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) );
		centerPanel.add( volumeBar );
		volumeBar.setPreferredSize( new Dimension( 200, 50 ) );

		final JPanel panel = new JPanel();
		panel.setLayout( new BorderLayout() );
		panel.add( toolBar, BorderLayout.PAGE_END );
		panel.add( centerPanel, BorderLayout.CENTER );
		return panel;
	}

	private void showExceptionDialog( final String message, final Exception e )
	{
		showExceptionDialog( this, message, e );
	}

	private void showExceptionDialog( final Component parent, final String message,
	                                  final Exception e )
	{
		if ( e == null )
		{
			JOptionPane.showMessageDialog( parent, message, getTitle(),
			                               JOptionPane.WARNING_MESSAGE );
		}
		else
		{
			e.printStackTrace();
			JOptionPane.showMessageDialog( parent, MessageFormat.format( ResourceBundle.getBundle( "helium" ).getString( "exception" ), message, e.getLocalizedMessage() ), e.getClass().getName(), JOptionPane.WARNING_MESSAGE );
		}
	}

	@Nullable
	private ImageIcon getIcon( final String name )
	{
		final URL resource = getClass().getClassLoader().getResource( name );
		return resource == null ? null : new ImageIcon( resource );
	}
}
