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
import java.nio.charset.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import javax.sound.sampled.*;

import org.jetbrains.annotations.*;
import org.json.*;

/**
 * Application configuration.
 *
 * @author Gerrit Meinders
 */
public class Config
{
	private String mixerName;

	private AudioFormat audioFormat;

	private boolean normalize;

	private double maximumGain;

	private double windowSize;

	private boolean normalizePerChannel;

	@Nullable
	private EncodingFormat encodingFormat;

	private File storageFolder;

	private NamingScheme namingScheme;

	public Config()
	{
		mixerName = null;
		audioFormat = parseAudioFormat( null );
		normalize = false;
		windowSize = 2.0;
		maximumGain = 10.0;
		normalizePerChannel = false;
		encodingFormat = null;
		storageFolder = new File( "." );
		namingScheme = new NamingScheme();
		namingScheme.setFormat( "\"recording\" date(yyyyMMdd) sequence" );
	}

	public Config( final Config original )
	{
		mixerName = original.mixerName;
		audioFormat = original.audioFormat;
		normalize = original.normalize;
		maximumGain = original.maximumGain;
		windowSize = original.windowSize;
		normalizePerChannel = original.normalizePerChannel;
		encodingFormat = ( original.encodingFormat == null ) ? null : original.encodingFormat.clone();
		storageFolder = original.storageFolder;
		namingScheme = new NamingScheme( original.namingScheme );
	}

	public String getMixerName()
	{
		return mixerName;
	}

	public void setMixerName( final String mixerName )
	{
		this.mixerName = mixerName;
	}

	/**
	 * Returns the format that audio is recorded in. If an encoding format is
	 * specified (see {@link #getEncodingFormat()}), this format is not
	 * actually written to storage.
	 *
	 * @return Audio format of recorded audio.
	 */
	public AudioFormat getAudioFormat()
	{
		return audioFormat;
	}

	public void setAudioFormat( final AudioFormat audioFormat )
	{
		this.audioFormat = audioFormat;
	}

	public boolean isNormalize()
	{
		return normalize;
	}

	public void setNormalize( final boolean normalize )
	{
		this.normalize = normalize;
	}

	public double getMaximumGain()
	{
		return maximumGain;
	}

	public void setMaximumGain( final double maximumGain )
	{
		this.maximumGain = maximumGain;
	}

	public double getWindowSize()
	{
		return windowSize;
	}

	public void setWindowSize( final double windowSize )
	{
		this.windowSize = windowSize;
	}

	public boolean isNormalizePerChannel()
	{
		return normalizePerChannel;
	}

	public void setNormalizePerChannel( final boolean normalizePerChannel )
	{
		this.normalizePerChannel = normalizePerChannel;
	}

	/**
	 * Returns the format used to encode audio recordings before they are
	 * written to storage.
	 *
	 * @return Format used to encode the audio.
	 */
	@Nullable
	public EncodingFormat getEncodingFormat()
	{
		return encodingFormat;
	}

	public void setEncodingFormat( @Nullable final EncodingFormat encodingFormat )
	{
		this.encodingFormat = encodingFormat;
	}

	public File getStorageFolder()
	{
		return storageFolder;
	}

	public void setStorageFolder( final File storageFolder )
	{
		this.storageFolder = storageFolder;
	}

	public NamingScheme getNamingScheme()
	{
		return namingScheme;
	}

	public void setNamingScheme( final NamingScheme namingScheme )
	{
		this.namingScheme = namingScheme;
	}

	public void load()
		throws IOException
	{
		final File file = new File( "config.json" );
		if ( file.exists() )
		{
			try ( final InputStream in = new FileInputStream( file ) )
			{
				load( new JSONObject( new JSONTokener( new InputStreamReader( in, StandardCharsets.UTF_8 ) ) ) );
			}
		}
		else
		{
			try ( final OutputStream out = new FileOutputStream( file );
			      final Writer writer = new OutputStreamWriter( out, StandardCharsets.UTF_8 ) )
			{
				save().write( writer, 2, 0 );
			}
		}
	}

	private void load( final JSONObject json )
	{
		mixerName = json.optString( "mixer", null );

		audioFormat = parseAudioFormat( json.optJSONObject( "audioFormat" ) );

		/*
		 * Normalization settings.
		 */
		final JSONObject jsonNormalize = json.optJSONObject( "normalize" );
		normalize = jsonNormalize != null;
		if ( normalize )
		{
			normalizePerChannel = jsonNormalize.optBoolean( "normalizePerChannel", normalizePerChannel );
			maximumGain = jsonNormalize.optDouble( "maximumGain", maximumGain );
			windowSize = jsonNormalize.optDouble( "windowSize", windowSize );
		}

		/*
		 * Audio recorder: encoding settings.
		 */
		final JSONObject jsonEncode = json.optJSONObject( "encode" );
		if ( jsonEncode != null )
		{
			final String formatName = jsonEncode.optString( "format", "wave" );

			switch ( formatName )
			{
				case "wave":
				{
					encodingFormat = new EncodingFormat.Wave();
					break;
				}
				case "mp3":
				{
					final EncodingFormat.MP3 format = new EncodingFormat.MP3();
					final Mp3Options options = format.getOptions();

					options.setExecutable( new File( jsonEncode.optString( "lameExecutable", options.getExecutable().toString() ) ) );

					final String mode = jsonEncode.optString( "mode", null );
					if ( "stereo".equals( mode ) )
					{
						options.setMode( Mp3Options.Mode.STEREO );
					}
					else if ( "joint-stereo".equals( mode ) )
					{
						options.setMode( Mp3Options.Mode.JOINT_STEREO );
					}
					else if ( "mono".equals( mode ) )
					{
						options.setMode( Mp3Options.Mode.MONO );
					}

					final JSONObject bitRate = jsonEncode.optJSONObject( "bitRate" );
					if ( bitRate != null )
					{
						final Number constant = bitRate.optNumber( "constant", null );
						final Number average = bitRate.optNumber( "average", null );
						final Number variable = bitRate.optNumber( "variable", null );

						if ( constant != null )
						{
							options.setBitRate( new Mp3Options.ConstantBitRate( constant.intValue() ) );
						}
						else if ( average != null )
						{
							options.setBitRate( new Mp3Options.AverageBitRate( average.intValue() ) );
						}
						else if ( variable != null )
						{
							options.setBitRate( new Mp3Options.VariableBitRate( variable.intValue() ) );
						}
					}

					encodingFormat = format;
					break;
				}
			}
		}

		/*
		 * Audio recorder: storage settings.
		 */
		final JSONObject jsonStore = json.optJSONObject( "store" );
		if ( jsonStore != null )
		{
			storageFolder = new File( jsonStore.optString( "folder", "" ) );

			final JSONObject jsonNamingScheme = jsonStore.optJSONObject( "namingScheme" );
			if ( jsonNamingScheme != null )
			{
				namingScheme = new NamingScheme();
				namingScheme.setSeparator( jsonNamingScheme.optString( "separator", namingScheme.getSeparator() ) );

				final JSONArray jsonElements = jsonNamingScheme.optJSONArray( "elements" );
				if ( jsonElements != null )
				{
					final List<NamingScheme.Element> elements = namingScheme.getElements();
					for ( final Object element : jsonElements )
					{
						if ( element instanceof String )
						{
							elements.add( new NamingScheme.StringElement( (String)element ) );
						}
						else if ( element instanceof JSONObject )
						{
							final JSONObject jsonElement = (JSONObject)element;
							final String date = jsonElement.optString( "date", null );
							final boolean sequence = jsonElement.optBoolean( "sequence", false );
							if ( date != null )
							{
								elements.add( new NamingScheme.DateElement( date ) );
							}
							else if ( sequence )
							{
								elements.add( new NamingScheme.SequenceElement() );
							}
						}
					}
				}
			}
		}
	}

	private static AudioFormat parseAudioFormat( @Nullable final JSONObject json )
	{
		int sampleRate = 44100;
		int bitsPerSample = 16;
		int channels = 2;
		boolean bigEndian = false;

		if ( json != null )
		{
			sampleRate = json.optInt( "sampleRate", sampleRate );
			bitsPerSample = json.optInt( "bitsPerSample", bitsPerSample );
			channels = json.optInt( "channels", channels );
			bigEndian = json.optBoolean( "bigEndian", bigEndian );
		}

		return new AudioFormat( AudioFormat.Encoding.PCM_SIGNED, sampleRate, bitsPerSample, channels, bitsPerSample * channels / 8, sampleRate, bigEndian );
	}

	@NotNull
	private JSONObject save()
	{
		final JSONObject result = new JSONObject();

		result.put( "mixer", mixerName );

		result.put( "audioFormat", new JSONObject()
			.put( "sampleRate", audioFormat.getSampleRate() )
			.put( "bitsPerSample", audioFormat.getSampleSizeInBits() )
			.put( "channels", audioFormat.getChannels() )
			.put( "bigEndian", audioFormat.isBigEndian() ) );


		if ( normalize )
		{
			result.put( "normalize", new JSONObject()
				.put( "normalizePerChannel", normalizePerChannel )
				.put( "maximumGain", maximumGain )
				.put( "windowSize", windowSize ) );
		}

		if ( encodingFormat != null )
		{
			result.put( "encode", encodingFormat.save() );
		}

		final JSONObject jsonStore = new JSONObject();
		jsonStore.put( "folder", storageFolder.toString() );
		if ( namingScheme != null )
		{
			jsonStore.put( "namingScheme", namingScheme.save() );
		}
		result.put( "store", jsonStore );

		return result;
	}

	/**
	 * Specifies the format used to encode audio recordings.
	 *
	 * @author Gerrit Meinders
	 */
	public abstract static class EncodingFormat
		implements Cloneable
	{
		@Override
		public EncodingFormat clone()
		{
			try
			{
				return (EncodingFormat)super.clone();
			}
			catch ( final CloneNotSupportedException e )
			{
				throw new AssertionError( e );
			}
		}

		public abstract JSONObject save();

		public static final class Wave
			extends EncodingFormat
		{
			public Wave()
			{
				// TODO: Add format options.
			}

			@Override
			public JSONObject save()
			{
				return new JSONObject().put( "format", "wave" );
			}
		}

		public static final class MP3
			extends EncodingFormat
		{
			private Mp3Options options;

			public MP3()
			{
				options = new Mp3Options();
			}

			public Mp3Options getOptions()
			{
				return options;
			}

			public void setOptions( final Mp3Options options )
			{
				this.options = options;
			}

			@Override
			public JSONObject save()
			{
				final JSONObject result = new JSONObject();

				result.put( "lameExecutable", options.getExecutable().toString() );

				switch ( options.getMode() )
				{
					case STEREO:
						result.put( "mode", "stereo" );
						break;
					case JOINT_STEREO:
						result.put( "mode", "joint-stereo" );
						break;
					case MONO:
						result.put( "mode", "mono" );
						break;
				}

				result.put( "bitRate", options.getBitRate().save() );

				return result.put( "format", "wave" );
			}
		}
	}

	/**
	 * Naming scheme for files in which audio recordings are stored.
	 *
	 * @author Gerrit Meinders
	 */
	public static class NamingScheme
	{
		private List<Element> elements;

		private String separator;

		public NamingScheme()
		{
			elements = new ArrayList<>();
			separator = "-";
		}

		public NamingScheme( final NamingScheme original )
		{
			elements = new ArrayList<>( original.elements );
			separator = original.separator;
		}

		public List<Element> getElements()
		{
			return elements;
		}

		public String getSeparator()
		{
			return separator;
		}

		public void setSeparator( final String separator )
		{
			this.separator = separator;
		}

		public File getFile( @Nullable File storageFolder, @Nullable final EncodingFormat format )
		{
			if ( storageFolder == null )
			{
				storageFolder = new File( "." ).getAbsoluteFile();
			}

			final StringBuilder name = new StringBuilder();

			if ( elements.isEmpty() )
			{
				name.append( "output" );

			}
			else
			{
				for ( final Iterator<Element> i = elements.iterator(); i.hasNext(); )
				{
					final Element element = i.next();
					element.append( name, storageFolder );

					if ( i.hasNext() )
					{
						name.append( separator );
					}
				}
			}

			if ( format instanceof EncodingFormat.MP3 )
			{
				name.append( ".mp3" );
			}
			else if ( format instanceof EncodingFormat.Wave )
			{
				name.append( ".wav" );
			}

			return new File( storageFolder, name.toString() );
		}

		public String getFormat()
		{
			final StringBuilder builder = new StringBuilder();

			for ( final Element element : elements )
			{
				if ( builder.length() > 0 )
				{
					builder.append( " " );
				}

				if ( element instanceof StringElement )
				{
					final StringElement stringElement = (StringElement)element;
					builder.append( '"' );
					builder.append( stringElement.value );
					builder.append( '"' );

				}
				else if ( element instanceof DateElement )
				{
					final DateElement dateElement = (DateElement)element;
					builder.append( "date(" );
					builder.append( dateElement.format.toPattern() );
					builder.append( ")" );

				}
				else if ( element instanceof SequenceElement )
				{
					final SequenceElement sequenceElement = (SequenceElement)element;
					builder.append( "sequence" );
				}
			}

			return builder.toString();
		}

		public void setFormat( final String format )
		{
			/*
			 * Quoted string (group 2) or identifier (group 4) with optional
			 * parameter (group 6).
			 */
			final String regex = "(\"([^\"]*)\")+|((\\w+)(\\(([\\w/-]*)\\))?)";

			final Pattern pattern = Pattern.compile( regex );
			final Matcher matcher = pattern.matcher( format );

			elements.clear();

			while ( matcher.find() )
			{
				final String string = matcher.group( 2 );
				final String identifier = matcher.group( 4 );
				final String parameter = matcher.group( 6 );

				if ( string != null )
				{
					elements.add( new StringElement( string ) );
				}
				else if ( "date".equals( identifier ) )
				{
					elements.add( new DateElement( parameter ) );
				}
				else if ( "sequence".equals( identifier ) )
				{
					elements.add( new SequenceElement() );
				}
				else
				{
					throw new IllegalArgumentException( matcher.group( 0 ) );
				}
			}
		}

		public JSONObject save()
		{
			final JSONObject result = new JSONObject();
			result.put( "separator", separator );
			for ( final Element element : elements )
			{
				result.append( "elements", element.save() );
			}
			return result;
		}

		public abstract static class Element
		{
			abstract void append( StringBuilder result, File storageFolder );

			public abstract Object save();
		}

		public static final class StringElement
			extends Element
		{
			private String value;

			public StringElement( final String value )
			{
				this.value = value;
			}

			public String getValue()
			{
				return value;
			}

			public void setValue( final String value )
			{
				this.value = value;
			}

			@Override
			void append( final StringBuilder result, final File storageFolder )
			{
				result.append( value );
			}

			@Override
			public Object save()
			{
				return value;
			}
		}

		public static final class DateElement
			extends Element
		{
			private SimpleDateFormat format;

			public DateElement( final String format )
			{
				this( new SimpleDateFormat( format ) );
			}

			public DateElement( final SimpleDateFormat format )
			{
				this.format = format;
			}

			public SimpleDateFormat getFormat()
			{
				return format;
			}

			public void setFormat( final SimpleDateFormat format )
			{
				this.format = format;
			}

			@Override
			void append( final StringBuilder result, final File storageFolder )
			{
				result.append( format.format( new Date() ) );
			}

			@Override
			public Object save()
			{
				return new JSONObject().put( "date", format.toPattern() );
			}
		}

		public static final class SequenceElement
			extends Element
		{
			@Override
			void append( final StringBuilder result, final File storageFolder )
			{
				final int inputLength = result.length();
				final String input = result.toString();

				final String[] sequenceFiles = storageFolder.list( ( dir, name ) -> name.startsWith( input ) );

				int sequenceNumber = 1;

				if ( ( sequenceFiles != null ) && ( sequenceFiles.length > 0 ) )
				{
					do
					{
						result.append( sequenceNumber++ );
						final String candidate = result.toString();

						for ( final String file : sequenceFiles )
						{
							if ( file.startsWith( candidate ) )
							{
								result.setLength( inputLength );
								break;
							}
						}

					}
					while ( result.length() == inputLength );

				}
				else
				{
					result.append( sequenceNumber );
				}
			}

			@Override
			public Object save()
			{
				return new JSONObject().put( "sequence", true );
			}
		}
	}
}
