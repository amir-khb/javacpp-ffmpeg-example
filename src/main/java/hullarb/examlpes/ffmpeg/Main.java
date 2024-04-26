package hullarb.examlpes.ffmpeg;

import org.bytedeco.javacpp.*;

import java.io.IOException;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avfilter.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.presets.avutil.AVERROR_EAGAIN;

public class Main {
    static class FilteringContext {
        AVFilterContext bufferSinkContext;
        AVFilterContext bufferSourceContext;
        AVFilterGraph filterGraph;
    }

    static Transcoding.FilteringContext[] filteringContexts;

    static class StreamContext {
        AVCodecContext decoderContext;
        AVCodecContext encoderContext;
    }

    static Transcoding.StreamContext[] streamContexts;

    static AVFormatContext inputFormatContext;
    static AVFormatContext outputFormatContext;

    static void check(int err) {
        if (err < 0) {
            BytePointer e = new BytePointer(512);
            av_strerror(err, e, 512);
            throw new RuntimeException(e.getString().substring(0, (int) BytePointer.strlen(e)) + ":" + err);
        }
    }

    static void openInput(String fileName) {
        inputFormatContext = new AVFormatContext(null);
        check(avformat_open_input(inputFormatContext, fileName, null, null));
        check(avformat_find_stream_info(inputFormatContext, (PointerPointer) null));
        streamContexts = new Transcoding.StreamContext[inputFormatContext.nb_streams()];
        for (int i = 0; i < inputFormatContext.nb_streams(); i++) {
            streamContexts[i] = new Transcoding.StreamContext();
            AVStream stream = inputFormatContext.streams(i);
            AVCodec decoder = avcodec_find_decoder(stream.codecpar().codec_id());
            if (decoder == null) new RuntimeException("Unexpected decore: " + stream.codecpar().codec_id());
            AVCodecContext codecContext = avcodec_alloc_context3(decoder);
            check(avcodec_parameters_to_context(codecContext, stream.codecpar()));
            if (codecContext.codec_type() == AVMEDIA_TYPE_VIDEO || codecContext.codec_type() == AVMEDIA_TYPE_AUDIO) {
                if (codecContext.codec_type() == AVMEDIA_TYPE_VIDEO) {
                    codecContext.framerate(av_guess_frame_rate(inputFormatContext, stream, null));
                }
                check(avcodec_open2(codecContext, decoder, (AVDictionary) null));
            }
            streamContexts[i].decoderContext = codecContext;
        }
        av_dump_format(inputFormatContext, 0, fileName, 0);
    }

    static AVFormatContext openOutput(String fileName) {
        outputFormatContext = new AVFormatContext(null);
        check(avformat_alloc_output_context2(outputFormatContext, null, null, fileName));
        for (int i = 0; i < inputFormatContext.nb_streams(); i++) {
            AVCodec c = new AVCodec(null);
            AVStream outStream = avformat_new_stream(outputFormatContext, c);
            AVStream inStream = inputFormatContext.streams(i);
            AVCodecContext decoderContext = streamContexts[i].decoderContext;
            if (decoderContext.codec_type() == AVMEDIA_TYPE_VIDEO ||
                    decoderContext.codec_type() == AVMEDIA_TYPE_AUDIO) {
                AVCodec encoder;
                if (decoderContext.codec_type() == AVMEDIA_TYPE_VIDEO) {
                    encoder = avcodec_find_encoder(AV_CODEC_ID_MPEG4); // Use mp4 video codec
                } else {
                    encoder = avcodec_find_encoder(decoderContext.codec_id());
                }
                AVCodecContext encoderContext = avcodec_alloc_context3(encoder);
                if (decoderContext.codec_type() == AVMEDIA_TYPE_VIDEO) {
                    encoderContext.height(decoderContext.height());
                    encoderContext.width(decoderContext.width());
                    encoderContext.sample_aspect_ratio(decoderContext.sample_aspect_ratio());
                    if (encoder.pix_fmts() != null && encoder.pix_fmts().asBuffer() != null) {
                        encoderContext.pix_fmt(encoder.pix_fmts().get(0));
                    } else {
                        encoderContext.pix_fmt(decoderContext.pix_fmt());
                    }
                    encoderContext.time_base(av_inv_q(decoderContext.framerate()));
                } else {
                    encoderContext.sample_rate(decoderContext.sample_rate());
                    encoderContext.channel_layout(decoderContext.channel_layout());
                    encoderContext.channels(av_get_channel_layout_nb_channels(encoderContext.channel_layout()));
                    encoderContext.sample_fmt(encoder.sample_fmts().get(0));
                    encoderContext.time_base(av_make_q(1, encoderContext.sample_rate()));
                }

                check(avcodec_open2(encoderContext, encoder, (AVDictionary) null));
                check(avcodec_parameters_from_context(outStream.codecpar(), encoderContext));
                if ((outputFormatContext.oformat().flags() & AVFMT_GLOBALHEADER) == AVFMT_GLOBALHEADER) {
                    encoderContext.flags(encoderContext.flags() | CODEC_FLAG_GLOBAL_HEADER);
                }
                outStream.time_base(encoderContext.time_base());
                streamContexts[i].encoderContext = encoderContext;
            } else {
                if (decoderContext.codec_type() == AVMEDIA_TYPE_UNKNOWN) {
                    throw new RuntimeException();
                } else {
                    check(avcodec_parameters_copy(outStream.codecpar(), inStream.codecpar()));
                    outStream.time_base(inStream.time_base());
                }
            }
        }
        av_dump_format(outputFormatContext, 0, fileName, 1);

        if ((outputFormatContext.flags() & AVFMT_NOFILE) != AVFMT_NOFILE) {
            AVIOContext c = new AVIOContext();
            check(avio_open(c, fileName, AVIO_FLAG_WRITE));
            outputFormatContext.pb(c);
        }

        check(avformat_write_header(outputFormatContext, (AVDictionary) null));
        return outputFormatContext;
    }

    public static void main(String[] args) throws IOException {

//        if (args.length < 2) {
//            System.out.println("Usage:Transcoding <input> <output>");
//            System.exit(-1);
//        }
        // Register all formats and codecs
        av_register_all();
        avfilter_register_all();

        openInput("C:\\Users\\amirkhb\\IdeaProjects\\javacpp-ffmpeg-example_Amir\\src\\main\\java\\hullarb\\examlpes\\ffmpeg\\a.flv");
        openOutput("C:\\Users\\amirkhb\\IdeaProjects\\javacpp-ffmpeg-example_Amir\\src\\main\\java\\hullarb\\examlpes\\ffmpeg\\b.mp4");
//        initFilters();
//        try {
//            int[] gotFrame = new int[1];
//            AVPacket packet = new AVPacket();
//            while (av_read_frame(inputFormatContext, packet) >= 0) {
//                try {
//                    int streamIndex = packet.stream_index();
//                    int type = inputFormatContext.streams(streamIndex).codecpar().codec_type();
//                    if (filteringContexts[streamIndex].filterGraph != null) {
//                        AVFrame frame = av_frame_alloc();
//                        try {
//
//                            av_packet_rescale_ts(packet, inputFormatContext.streams(streamIndex).time_base(),
//                                    streamContexts[streamIndex].decoderContext.time_base());
//                            if (type == AVMEDIA_TYPE_VIDEO) {
//                                check(avcodec_decode_video2(streamContexts[streamIndex].decoderContext, frame, gotFrame, packet));
//                            } else {
//                                check(avcodec_decode_audio4(streamContexts[streamIndex].decoderContext, frame, gotFrame, packet));
//                            }
//                            if (gotFrame[0] != 0) {
//                                frame.pts(frame.best_effort_timestamp());
//                                filterEncodeWriteFrame(frame, streamIndex);
//                            }
//                        } finally {
//                            av_frame_free(frame);
//                        }
//                    } else {
//                        av_packet_rescale_ts(packet, inputFormatContext.streams(streamIndex).time_base(),
//                                outputFormatContext.streams(streamIndex).time_base());
//                        check(av_interleaved_write_frame(outputFormatContext, packet));
//                    }
//                } finally {
//                    av_packet_unref(packet);
//                }
//            }
//
//
//            for (int i = 0; i < inputFormatContext.nb_streams(); i++) {
//                if (filteringContexts[i].filterGraph == null) {
//                    continue;
//                }
//                filterEncodeWriteFrame(null, i);
//                flushEncoder(i);
//            }
//
//            av_write_trailer(outputFormatContext);
//
//
//        } finally {
//            for (int i = 0; i < inputFormatContext.nb_streams(); i++) {
//                avcodec_free_context(streamContexts[i].decoderContext);
//                if (outputFormatContext != null && outputFormatContext.nb_streams() > 0 &&
//                        outputFormatContext.streams(i) != null && streamContexts[i].encoderContext != null) {
//                    avcodec_free_context(streamContexts[i].encoderContext);
//                }
//                if (filteringContexts != null && filteringContexts[i].filterGraph != null) {
//                    avfilter_graph_free(filteringContexts[i].filterGraph);
//                }
//            }
//            avformat_close_input(inputFormatContext);
//            if (outputFormatContext != null && (outputFormatContext.oformat().flags() & AVFMT_NOFILE) != AVFMT_NOFILE) {
//                avio_closep(outputFormatContext.pb());
//            }
//            avformat_free_context(outputFormatContext);
//        }
    }

}
