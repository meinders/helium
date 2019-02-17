/*
 * Copyright 2013-2019 Gerrit Meinders
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
import java.util.*;
import java.util.concurrent.*;
import javax.sound.sampled.*;
import javax.swing.*;

import helium.Config.*;
import org.jetbrains.annotations.*;

/**
 * Provides a simple interface for recording audio.
 *
 * @author Gerrit Meinders
 */
public class Recorder
{
	private Config config;

	private TargetDataLineInputStream recordIn;

	private ExecutorService threadPool;

	private Collection<AmplitudeListener> amplitudeListeners;

	private Collection<GainListener> gainListeners;

	public Recorder( final Config config )
	{
		this.config = config;

		amplitudeListeners = new ArrayList<>();
		gainListeners = new ArrayList<>();
	}

	public void setConfig( final Config config )
	{
		this.config = config;
	}

	public void start()
		throws LineUnavailableException, IOException
	{
		final AudioFormat audioFormat = config.getAudioFormat();
		final EncodingFormat encodingFormat = config.getEncodingFormat();

		final List<InputStream> inputs = new ArrayList<>();
		final List<OutputStream> outputs = new ArrayList<>();

		/*
		 * Storage
		 */
		final File storageFolder = config.getStorageFolder();
		final NamingScheme namingScheme = config.getNamingScheme();
		final File file = namingScheme.getFile( storageFolder, encodingFormat );
		System.out.println( "Writing output to " + file );
		OutputStream out = new BufferedOutputStream( new FileOutputStream( file ) );

		/*
		 * Recording
		 */
		Mixer.Info mixer = getMixer( config.getMixerName() );
		if ( mixer == null )
		{
			System.out.println( "No mixer is configured." );
			final Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
			if ( mixerInfos.length == 0 )
			{
				System.out.println( "No mixers are available." );
			}
			else
			{
				System.out.println( "Available mixers are:" );
				for ( final Mixer.Info mixerInfo : mixerInfos )
				{
					System.out.println( " - " + mixerInfo.getName() );
				}

				mixer = mixerInfos[ 0 ];
				System.out.println( "Using first available mixer: " + mixer.getName() );
			}
		}

		final TargetDataLine targetDataLine = mixer == null ? AudioSystem.getTargetDataLine( audioFormat ) : AudioSystem.getTargetDataLine( audioFormat, mixer );
		System.out.println( "Recording from " + targetDataLine.getLineInfo() );
		System.out.println( "Audio format: " + audioFormat );
		System.out.println( "Buffer size: " + targetDataLine.getBufferSize()
		                    + " bytes" );
		recordIn = new TargetDataLineInputStream( targetDataLine, audioFormat );
		inputs.add( new BufferedInputStream( recordIn ) );

		/*
		 * Encoding
		 */
		if ( encodingFormat != null )
		{
			if ( encodingFormat instanceof EncodingFormat.MP3 )
			{
				System.out.println( "Encoding as MP3" );
				final EncodingFormat.MP3 mp3Format = (EncodingFormat.MP3)encodingFormat;
				final LameOutputStream lameOut = new LameOutputStream( out, mp3Format.getOptions() );
				out = new WaveOutputStream( lameOut, audioFormat );
			}
			else if ( encodingFormat instanceof EncodingFormat.Wave )
			{
				System.out.println( "Encoding as Wave" );
				out = new WaveOutputStream( out, audioFormat );
			}
		}

		/*
		 * Normalization
		 */
		final MonitorAudioOutputStream monitor;
		if ( config.isNormalize() )
		{
			System.out.println( "Normalizing enabled" );
			final NormalizingOutputStream normalizeOut = new NormalizingOutputStream(
				out, audioFormat, config.getWindowSize(),
				config.getMaximumGain(),
				config.isNormalizePerChannel() );

			if ( !Boolean.parseBoolean( System.getProperty( "helium.dcOffsetEnabled", "true" ) ) )
			{
				normalizeOut.setDCOffsetEnabled( false );
			}

			out = normalizeOut;
			monitor = normalizeOut;

			normalizeOut.addGainListener( this::fireGainChange );

		}
		else
		{
			monitor = new MonitorAudioOutputStream( out, audioFormat );
			out = monitor;
		}

		/*
		 * Monitor volume and forward volume events.
		 */
		monitor.addAmplitudeListener( this::fireAmplitudeChange );

		/*
		 * Connect inputs to outputs with pipes.
		 */
		outputs.add( out );

		if ( inputs.size() != outputs.size() )
		{
			throw new AssertionError( "inputs.size() != outputs.size(): "
			                          + inputs.size() + " != " + outputs.size() );
		}

		threadPool = Executors.newFixedThreadPool( inputs.size() );

		for ( int i = 0; i < inputs.size(); i++ )
		{
			final Pipe pipe = new Pipe( inputs.get( i ), outputs.get( i ) );
			threadPool.execute( pipe );
		}

		recordIn.start();
	}

	@Nullable
	private static Mixer.Info getMixer( final String mixerName )
	{
		Mixer.Info mixer = null;
		if ( mixerName != null )
		{
			for ( final Mixer.Info mixerInfo : AudioSystem.getMixerInfo() )
			{
				if ( mixerName.equals( mixerInfo.getName() ) )
				{
					mixer = mixerInfo;
				}
			}
		}
		return mixer;
	}

	public boolean isStarted()
	{
		return ( recordIn != null ) && recordIn.isStarted();
	}

	public void stop()
		throws IOException
	{
		if ( recordIn != null )
		{
			System.out.println( "Stop" );
			recordIn.stop();
			System.out.println( "Drain" );
			recordIn.drain();
			System.out.println( "Close" );
			recordIn.close();
		}
		if ( threadPool != null )
		{
			System.out.println( "Shutdown" );
			threadPool.shutdown();
		}
		System.out.println( "Recorder stopped." );
	}

	public void addAmplitudeListener( final AmplitudeListener volumeListener )
	{
		amplitudeListeners.add( volumeListener );
	}

	public void removeAmplitudeListener( final AmplitudeListener volumeListener )
	{
		amplitudeListeners.remove( volumeListener );
	}

	private void fireAmplitudeChange( final int channel, final double amplitude )
	{
		SwingUtilities.invokeLater( () -> {
			for ( final AmplitudeListener listener : amplitudeListeners )
			{
				listener.amplitudeChanged( channel, amplitude );
			}
		} );
	}

	public void addGainListener( final GainListener gainListener )
	{
		gainListeners.add( gainListener );
	}

	public void removeGainListener( final GainListener gainListener )
	{
		gainListeners.remove( gainListener );
	}

	private void fireGainChange( final int channel, final double gain )
	{
		SwingUtilities.invokeLater( () -> {
			for ( final GainListener listener : gainListeners )
			{
				listener.gainChanged( channel, gain );
			}
		} );
	}
}
