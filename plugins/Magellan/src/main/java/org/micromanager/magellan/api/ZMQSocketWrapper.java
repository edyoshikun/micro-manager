package org.micromanager.magellan.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.acq.Acquisition;
import org.micromanager.magellan.misc.Log;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

//Base class that wraps a ZMQ socket and implmenets type conversions as well as the impicit 
//JSON message syntax
public abstract class ZMQSocketWrapper {

   private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
   public final static Map<Class<?>, Class<?>> primitiveClassMap_ = new HashMap<Class<?>, Class<?>>();

   static {
      primitiveClassMap_.put(Boolean.class, boolean.class);
      primitiveClassMap_.put(Byte.class, byte.class);
      primitiveClassMap_.put(Short.class, short.class);
      primitiveClassMap_.put(Character.class, char.class);
      primitiveClassMap_.put(Integer.class, int.class);
      primitiveClassMap_.put(Long.class, long.class);
      primitiveClassMap_.put(Float.class, float.class);
      primitiveClassMap_.put(Double.class, double.class);
   }

   private static Class[] SERIALIZABLE_CLASSES = new Class[]{String.class, Void.TYPE, Short.TYPE,
      Long.TYPE, Integer.TYPE, Float.TYPE, Double.TYPE, Boolean.TYPE,
      byte[].class, double[].class, int[].class, TaggedImage.class, MagellanAcquisitionAPI.class,
      List.class};

   protected static ZContext context_;
   protected SocketType type_;
   protected ZMQ.Socket socket_;
   protected String name_;

   public ZMQSocketWrapper(int port, String name, SocketType type) {
      type_ = type;
      name_ = name;
      if (context_ == null) {
         context_ = new ZContext();
      }
      initialize(port);
   }
   
   protected abstract void initialize(int port);

   /**
    * send a command from a Java client to a python server and wait on response
    */
   protected Object sendRequest(String request) {
      socket_.send(request);
      byte[] reply = socket_.recv();
      return deserialize(reply);
   }

   protected abstract byte[] parseAndExecuteCommand(String message) throws Exception;

   protected byte[] runMethod(Object obj,  JSONObject json) throws NoSuchMethodException, IllegalAccessException, JSONException {
      String methodName = json.getString("name");

      Class[] argClasses = new Class[json.getJSONArray("arguments").length()];
      Object[] argVals = new Object[json.getJSONArray("arguments").length()];
      for (int i = 0; i < argVals.length; i++) {
         //Converts onpbjects to primitives
         Class c = json.getJSONArray("arguments").get(i).getClass();
         if (primitiveClassMap_.containsKey(c)) {
            c = primitiveClassMap_.get(c);
         }
         argClasses[i] = c;
         argVals[i] = json.getJSONArray("arguments").get(i);
      }

      Method method = obj.getClass().getMethod(methodName, argClasses);
      Object result = null;
      try {
         result = method.invoke(obj, argVals);
      } catch (InvocationTargetException ex) {
         result = ex.getCause();
         Log.log(ex);
         
      }

      JSONObject serialized = new JSONObject();
      serialize(result, serialized);
      return serialized.toString().getBytes();
   }

   
   protected Object deserialize(byte[] message) {
      String s = new String(message);
     
      JSONObject json = null;
      try {
         json = new JSONObject(s);
      } catch (JSONException ex) {
         throw new RuntimeException("Problem turning message into JSON. Message was: " + s);
      }
      try {
         String type = json.getString("type");
         String value = json.getString("value");
         //TODO: decode values
         if (type.equals("object")) {
            String clazz = json.getString("class");
         } else if (type.equals("primitive")) {
           
         }
         return null;
         //TODO: return exception maybe?
      } catch (JSONException ex) {
         throw new RuntimeException("Message missing command field");
      }
      
   }
   
   /**
    * Serialize the object in some way that the client will know how to
    * deserialize
    */
   protected void serialize(Object o, JSONObject json) {
      try {
         if (o instanceof Exception) {
            json.put("type", "exception");
            json.put("value", ((Exception) o).getMessage());
         } else if (o instanceof String) {
            json.put("type", "string");
            json.put("value", o);
         } else if (o == null) {
            json.put("type", "none");
         } else if (o.getClass().equals(Long.class) || o.getClass().equals(Short.class)
                 || o.getClass().equals(Integer.class) || o.getClass().equals(Float.class)
                 || o.getClass().equals(Double.class) || o.getClass().equals(Boolean.class)) {
            json.put("type", "primitive");
            json.put("value", o);
         } else if (o.getClass().equals(TaggedImage.class)) {
            json.put("type", "object");
            json.put("class", "TaggedImage");
            json.put("value", new JSONObject());
            json.getJSONObject("value").put("pixel-type", (((TaggedImage) o).pix instanceof byte[]) ? "uint8" : "uint16");
            json.getJSONObject("value").put("tags", ((TaggedImage) o).tags);
            json.getJSONObject("value").put("pix", encodeArray(((TaggedImage) o).pix));
         } else if (o.getClass().equals(byte[].class)) {
            json.put("type", "byte-array");
            json.put("value", encodeArray(o));
         } else if (o.getClass().equals(double[].class)) {
            json.put("type", "double-array");
            json.put("value", encodeArray(o));
         } else if (o.getClass().equals(int[].class)) {
            json.put("type", "int-array");
            json.put("value", encodeArray(o));
         } else if (o.getClass().equals(float[].class)) {
            json.put("type", "float-array");
            json.put("value", encodeArray(o));
         } else if (o instanceof MagellanAcquisitionAPI) {                
            json.put("type", "object");
            json.put("class", "MagellanAcquisitionAPI");
            json.put("value", new JSONObject());
            json.getJSONObject("value").put("UUID", ((Acquisition) o).getUUID());
         } else if (Stream.of(o.getClass().getInterfaces()).anyMatch((Class t) -> t.equals(List.class))) {                
            json.put("type", "list");
            json.put("value", new JSONArray());
            for (Object element : (List) o) {
               JSONObject e = new JSONObject();
               json.getJSONArray("value").put(e);
               serialize(element, e);
            }
         } else {
            throw new RuntimeException("Unrecognized class return type");
         }
      } catch (JSONException e) {
         e.printStackTrace();
         Log.log(e);
      }
   }

   protected String encodeArray(Object array) {
      byte[] byteArray = null;
      if (array instanceof byte[]) {
         byteArray = (byte[]) array;
      } else if (array instanceof short[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((short[]) array)).length * Short.BYTES);
         buffer.order(BYTE_ORDER).asShortBuffer().put((short[]) array);
         byteArray = buffer.array();
      } else if (array instanceof int[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((int[]) array)).length * Integer.BYTES);
         buffer.order(BYTE_ORDER).asIntBuffer().put((int[]) array);
         byteArray = buffer.array();
      } else if (array instanceof double[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((double[]) array)).length * Double.BYTES);
         buffer.order(BYTE_ORDER).asDoubleBuffer().put((double[]) array);
         byteArray = buffer.array();
      } else if (array instanceof float[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((float[]) array)).length * Float.BYTES);
         buffer.order(BYTE_ORDER).asFloatBuffer().put((float[]) array);
         byteArray = buffer.array();
      }
      return Base64.getEncoder().encodeToString(byteArray);
   }

   /**
    * Check if return types and all argument types can be translated
    *
    * @param t
    * @return
    */
   private static boolean isValidMethod(Method t) {
      List<Class> l = new ArrayList<>();
      for (Class c : t.getParameterTypes()) {
         l.add(c);
      }
      l.add(t.getReturnType());
      for (Class c : l) {
         if (!Arrays.asList(SERIALIZABLE_CLASSES).contains(c)) {
            return false;
         }
      }
      return true;
   }

   /**
    * Go through all methods of the given class, filter the ones that can be
    * translated based on argument and return type, and put them into a big JSON
    * array that describes the API
    *
    * @return
    * @throws JSONException
    */
   protected static byte[] parseAPI(Class clazz) throws JSONException {
      //Collect all methods whose return types and arguments we know how to translate, and put them in a JSON array describing them
      Predicate<Method> methodFilter = (Method t) -> {
         return isValidMethod(t);
      };

      Method[] m = clazz.getDeclaredMethods();
      Stream<Method> s = Arrays.stream(m);
      s = s.filter(methodFilter);
      List<Method> validMethods = s.collect(Collectors.toList());
      JSONArray methodArray = new JSONArray();
      for (Method method : validMethods) {
         JSONObject methJSON = new JSONObject();
         methJSON.put("name", method.getName());
         methJSON.put("return-type", method.getReturnType().getCanonicalName());
         JSONArray args = new JSONArray();
         for (Class arg : method.getParameterTypes()) {
            args.put(arg.getCanonicalName());
         }
         methJSON.put("arguments", args);
         methodArray.put(methJSON);
      }
      return methodArray.toString().getBytes();
   }
}
