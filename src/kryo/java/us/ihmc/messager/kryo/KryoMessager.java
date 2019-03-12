package us.ihmc.messager.kryo;

import us.ihmc.log.LogTools;
import us.ihmc.messager.Message;
import us.ihmc.messager.Messager;
import us.ihmc.messager.MessagerAPIFactory.MessagerAPI;
import us.ihmc.messager.MessagerAPIFactory.Topic;
import us.ihmc.messager.MessagerStateListener;
import us.ihmc.messager.TopicListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A {@link Messager} implementation that uses Kryonet under the hood.
 *
 * With Kryo there must be a server and a client, so {@link KryoMessager#createServer} needs to be
 * called on one side and {@link KryoMessager#createClient} on the other.
 *
 * Sometimes the requested port is unavailable and you will need to select another.
 */
public class KryoMessager implements Messager
{
   private final MessagerAPI messagerAPI;
   private final KryoAdapter kryoAdapter;
   private MessagerUpdateThread messagerUpdateThread;

   private final ConcurrentHashMap<Topic<?>, List<AtomicReference<Object>>> inputVariablesMap = new ConcurrentHashMap<>();
   private final ConcurrentHashMap<Topic<?>, List<TopicListener<Object>>> topicListenersMap = new ConcurrentHashMap<>();
   private final List<MessagerStateListener> connectionStateListeners = new ArrayList<>();

   private boolean allowSelfSubmit = true;

   /**
    * Creates a KryoMessager server side using
    * {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)} under the hood.
    *
    * @param messagerAPI
    * @param tcpPort
    * @param name
    * @param updatePeriodMillis
    * @return
    */
   public static KryoMessager createServer(MessagerAPI messagerAPI, int tcpPort, String name, int updatePeriodMillis)
   {
      return new KryoMessager(messagerAPI, KryoAdapter.createServer(tcpPort), new DefaultMessagerUpdateThread(name, updatePeriodMillis));
   }

   /**
    * Creates a KryoMessager server that provides the user with a Runnable through {@link MessagerUpdateThread}
    * that updates the Kryo internals. The user is responsible for calling that runnable periodically.
    *
    * @param messagerAPI
    * @param tcpPort
    * @param messagerUpdateThread
    * @return
    */
   public static KryoMessager createServer(MessagerAPI messagerAPI, int tcpPort, MessagerUpdateThread messagerUpdateThread)
   {
      return new KryoMessager(messagerAPI, KryoAdapter.createServer(tcpPort), messagerUpdateThread);
   }

   /**
    * Creates a KryoMessager client side using
    * {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)} under the hood.
    *
    * The client side requires an address i.e. "localhost" or "192.168.1.3", etc.
    *
    * @param messagerAPI
    * @param serverAddress
    * @param tcpPort
    * @param name
    * @param updatePeriodMillis
    * @return
    */
   public static KryoMessager createClient(MessagerAPI messagerAPI, String serverAddress, int tcpPort, String name, int updatePeriodMillis)
   {
      return new KryoMessager(messagerAPI, KryoAdapter.createClient(serverAddress, tcpPort), new DefaultMessagerUpdateThread(name, updatePeriodMillis));
   }

   /**
    * Creates a KryoMessager client that provides the user with a Runnable through {@link MessagerUpdateThread}
    * that updates the Kryo internals. The user is responsible for calling that runnable periodically.
    *
    * @param messagerAPI
    * @param serverAddress
    * @param tcpPort
    * @param messagerUpdateThread
    * @return
    */
   public static KryoMessager createClient(MessagerAPI messagerAPI, String serverAddress, int tcpPort, MessagerUpdateThread messagerUpdateThread)
   {
      return new KryoMessager(messagerAPI, KryoAdapter.createClient(serverAddress, tcpPort), messagerUpdateThread);
   }

   private KryoMessager(MessagerAPI messagerAPI, KryoAdapter kryoAdapter, MessagerUpdateThread messagerUpdateThread)
   {
      this.messagerAPI = messagerAPI;
      this.kryoAdapter = kryoAdapter;
      this.messagerUpdateThread = messagerUpdateThread;

      kryoAdapter.setRecievedListener(object -> receiveMessage(object));
   }

   /** @inheritDoc */
   @Override
   public <T> void submitMessage(Message<T> message)
   {
      if (!messagerAPI.containsTopic(message.getTopicID()))
         throw new RuntimeException("The message is not part of this messager's API.");

      Topic<?> messageTopic = messagerAPI.findTopic(message.getTopicID());

      if (allowSelfSubmit)
         receiveMessage(message);

      if (!kryoAdapter.isConnected())
      {
         LogTools.warn("This messager is closed, message's topic: " + messageTopic.getName());
         return;
      }

      LogTools.debug("Submit message for topic: {}", messageTopic.getName());

      kryoAdapter.sendTCP(message);
   }

   private void receiveMessage(Object object)
   {
      if (object == null || !(object instanceof Message))
         return;

      Message message = (Message) object;

      if (!messagerAPI.containsTopic(message.getTopicID()))
         throw new RuntimeException("The message is not part of this messager's API.");

      Topic messageTopic = messagerAPI.findTopic(message.getTopicID());

      LogTools.debug("Packet received from network with message name: {}", messageTopic.getName());

      List<AtomicReference<Object>> inputVariablesForTopic = inputVariablesMap.get(messageTopic);
      if (inputVariablesForTopic != null)
         inputVariablesForTopic.forEach(variable -> variable.set(message.getMessageContent()));

      List<TopicListener<Object>> topicListeners = topicListenersMap.get(messageTopic);
      if (topicListeners != null)
         topicListeners.forEach(listener -> listener.receivedMessageForTopic(message.getMessageContent()));
   }

   /** @inheritDoc */
   @Override
   public <T> AtomicReference<T> createInput(Topic<T> topic, T defaultValue)
   {
      AtomicReference<T> boundVariable = new AtomicReference<>(defaultValue);

      List<AtomicReference<Object>> boundVariablesForTopic = inputVariablesMap.get(topic);
      if (boundVariablesForTopic == null)
      {
         boundVariablesForTopic = new ArrayList<>();
         inputVariablesMap.put(topic, boundVariablesForTopic);
      }
      boundVariablesForTopic.add((AtomicReference<Object>) boundVariable);
      return boundVariable;
   }

   /** @inheritDoc */
   @Override
   public <T> void registerTopicListener(Topic<T> topic, TopicListener<T> listener)
   {
      List<TopicListener<Object>> topicListeners = topicListenersMap.get(topic);
      if (topicListeners == null)
      {
         topicListeners = new ArrayList<>();
         topicListenersMap.put(topic, topicListeners);
      }
      topicListeners.add((TopicListener<Object>) listener);
   }

   /** @inheritDoc */
   @Override
   public void startMessager() throws Exception
   {
      LogTools.debug("Starting to connect KryoNet");
      kryoAdapter.connect();

      LogTools.debug("Waiting for KryoNet to connect");
      while (!isMessagerOpen())  // this is necessary before starting the messager update thread
      {                          // otherwise connection times out because multiple threads are calling
         Thread.yield();         // kryo.update()
      }

      LogTools.debug("Starting KryoNet update thread");
      messagerUpdateThread.start(() -> kryoAdapter.update());
   }

   /** @inheritDoc */
   @Override
   public void closeMessager() throws Exception
   {
      kryoAdapter.disconnect();
      messagerUpdateThread.stop();
   }

   /** @inheritDoc */
   @Override
   public boolean isMessagerOpen()
   {
      return kryoAdapter.isConnected();
   }

   /** @inheritDoc */
   @Override
   public void notifyMessagerStateListeners()
   {
      connectionStateListeners.forEach(listener -> listener.messagerStateChanged(isMessagerOpen()));
   }

   /** @inheritDoc */
   @Override
   public void registerMessagerStateListener(MessagerStateListener listener)
   {
      kryoAdapter.addConnectionStateListener(state -> listener.messagerStateChanged(state));
   }

   /** @inheritDoc */
   @Override
   public MessagerAPI getMessagerAPI()
   {
      return messagerAPI;
   }
}
