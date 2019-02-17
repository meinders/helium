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
import javax.sound.sampled.*;
import javax.swing.*;

public class MonitorAudioOutputStream
	extends FilterAudioOutputStream
{
	private int channel;

	private int channels;

	private int samples;

	private int samplesPerUpdate;

	private Collection<AmplitudeListener> amplitudeListeners;

	public MonitorAudioOutputStream( final OutputStream out, final AudioFormat format )
	{
		super( out, format );
		channel = 0;
		channels = format.getChannels();
		samples = 0;
		samplesPerUpdate = (int)format.getSampleRate() / 30;
		amplitudeListeners = new ArrayList<>();
	}

	@Override
	protected void writeSample( final int sample )
		throws IOException
	{
		writeSample( channel, sample );
	}

	/**
	 * Writes the given sample to the underlying output stream, possibly after
	 * processing it. The sample need not be written immediately, to allow for
	 * internal buffering.
	 *
	 * @param channel The channel that the sample originates from.
	 * @param sample  The sample to be written.
	 *
	 * @throws IOException if an I/O error occurs.
	 */
	protected void writeSample( final int channel, final int sample )
		throws IOException
	{
		monitorSampleOnWrite( sample );
		super.writeSample( sample );
	}

	protected void monitorSampleOnWrite( final int sample )
	{
		monitorSample( sample );
	}

	protected void monitorSample( final int sample )
	{
		if ( samples % samplesPerUpdate == 0 )
		{
			fireEvents( channel, sample );
		}

		channel++;
		channel %= channels;

		if ( channel == 0 )
		{
			samples++;
		}
	}

	protected void fireEvents( final int channel, final int sample )
	{
		final double maximum = sampleFormat.getMaximumAmplitude();
		final double amplitude = Math.abs( sample / maximum );
		fireAmplitudeChange( channel, amplitude );
	}

	public void addAmplitudeListener( final AmplitudeListener amplitudeListener )
	{
		amplitudeListeners.add( amplitudeListener );
	}

	public void removeAmplitudeListener( final AmplitudeListener amplitudeListener )
	{
		amplitudeListeners.remove( amplitudeListener );
	}

	protected void fireAmplitudeChange( final int channel, final double amplitude )
	{
		SwingUtilities.invokeLater( () -> {
			for ( final AmplitudeListener listener : amplitudeListeners )
			{
				listener.amplitudeChanged( channel, amplitude );
			}
		} );
	}
}
