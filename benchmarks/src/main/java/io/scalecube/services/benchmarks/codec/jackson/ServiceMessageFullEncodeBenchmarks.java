package io.scalecube.services.benchmarks.codec.jackson;

import com.codahale.metrics.Timer;
import io.netty.util.ReferenceCountUtil;
import io.scalecube.benchmarks.BenchmarksSettings;
import io.scalecube.services.api.ServiceMessage;
import io.scalecube.services.benchmarks.codec.ServiceMessageCodecBenchmarksState;
import io.scalecube.services.codec.ServiceMessageCodec;
import java.util.concurrent.TimeUnit;

public class ServiceMessageFullEncodeBenchmarks {

  /**
   * Main method.
   *
   * @param args - params of main method.
   */
  public static void main(String[] args) {
    BenchmarksSettings settings =
        BenchmarksSettings.from(args).durationUnit(TimeUnit.NANOSECONDS).build();
    new ServiceMessageCodecBenchmarksState.Jackson(settings)
        .runForSync(
            state -> {
              Timer timer = state.timer("timer");
              ServiceMessageCodec messageCodec = state.jacksonMessageCodec();
              ServiceMessage message = state.message();

              return i -> {
                Timer.Context timeContext = timer.time();
                Object result =
                    messageCodec.encodeAndTransform(
                        message,
                        (dataByteBuf, headersByteBuf) -> {
                          ReferenceCountUtil.release(dataByteBuf);
                          ReferenceCountUtil.release(headersByteBuf);
                          return dataByteBuf;
                        });
                timeContext.stop();
                return result;
              };
            });
  }
}
