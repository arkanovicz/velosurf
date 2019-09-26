package velosurf.util;

import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;

public class Base64Utils
{
  public static byte[] toBytes(Object value)
  {
    if (value == null || value instanceof byte[])
    {
      return (byte[]) value;
    }
    return String.valueOf(value).getBytes(StandardCharsets.UTF_8);
  }

  public static String toString(Object value)
  {
    return value == null ? null : value.toString();
  }

  public static String base64Encode(Object value)
  {
    if (value == null)
    {
      return null;
    }
    byte[] decoded = toBytes(value);
    byte[] encoded = Base64.encodeBase64URLSafe(decoded);
    return new String(encoded, StandardCharsets.UTF_8);
  }

  public static byte[] base64Decode(Object value)
  {
    if (value == null)
    {
      return null;
    }
    String encoded = toString(value);
    return Base64.decodeBase64(encoded);

  }

}
