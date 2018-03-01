package io.scalecube.ipc;

import static io.scalecube.ipc.Event.Topic.MessageWrite;
import static io.scalecube.ipc.Event.Topic.ReadError;
import static io.scalecube.ipc.Event.Topic.ReadSuccess;
import static io.scalecube.ipc.Event.Topic.WriteError;
import static io.scalecube.ipc.Event.Topic.WriteSuccess;

import io.scalecube.transport.Address;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public final class ChannelContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(ChannelContext.class);

  private static final ConcurrentMap<String, ChannelContext> idToChannelContext = new ConcurrentHashMap<>();

  private final Subject<Event, Event> subject = PublishSubject.<Event>create().toSerialized();
  private final Subject<Event, Event> closeSubject = PublishSubject.<Event>create().toSerialized();

  private final String id;
  private final Address address;

  private ChannelContext(String id, Address address) {
    Objects.requireNonNull(id);
    Objects.requireNonNull(address);
    this.id = id;
    this.address = address;
  }

  /**
   * Factory method for {@link ChannelContext} object. One and only one way to create it.
   * 
   * @param id channel context identity.
   * @param address a remote socket address of the channel context; must be resolved socket address.
   */
  public static ChannelContext create(String id, Address address) {
    ChannelContext channelContext = new ChannelContext(id, address);
    idToChannelContext.put(id, channelContext);
    LOGGER.debug("Created {}", channelContext);
    return channelContext;
  }

  public static ChannelContext getIfExist(String id) {
    return idToChannelContext.get(id);
  }

  public static void closeIfExist(String id) {
    Optional.ofNullable(ChannelContext.getIfExist(id)).ifPresent(ChannelContext::close);
  }

  public String getId() {
    return id;
  }

  public Address getAddress() {
    return address;
  }

  public Observable<Event> listen() {
    return subject.onBackpressureBuffer().asObservable();
  }

  public Observable<Event> listenReadSuccess() {
    return listen().filter(Event::isReadSuccess);
  }

  public Observable<Event> listenMessageWrite() {
    return listen().filter(Event::isMessageWrite);
  }

  public void postReadSuccess(ServiceMessage message) {
    subject.onNext(new Event.Builder(ReadSuccess, this).message(message).build());
  }

  public void postReadError(Throwable throwable) {
    subject.onNext(new Event.Builder(ReadError, this).error(throwable).build());
  }

  public void postMessageWrite(ServiceMessage message) {
    subject.onNext(new Event.Builder(MessageWrite, this).message(message).build());
  }

  public void postWriteError(Throwable throwable, ServiceMessage message) {
    subject.onNext(new Event.Builder(WriteError, this).error(throwable).message(message).build());
  }

  public void postWriteSuccess(ServiceMessage message) {
    subject.onNext(new Event.Builder(WriteSuccess, this).message(message).build());
  }

  /**
   * Issues close on this channel context: emits onCompleted on subject, and eventually removes itseld from the hash
   * map. Subsequent {@link #getIfExist(String)} would return null after this operation.
   */
  public void close() {
    subject.onCompleted();
    closeSubject.onCompleted();
    ChannelContext channelContext = idToChannelContext.remove(id);
    if (channelContext != null) {
      LOGGER.debug("Closed and removed {}", channelContext);
    }
  }

  public void listenClose(Consumer<ChannelContext> onClose) {
    closeSubject.subscribe(event -> {
    }, throwable -> onClose.accept(this), () -> onClose.accept(this));
  }

  @Override
  public String toString() {
    return "ChannelContext{id=" + id + ", address=" + address + "}";
  }
}
