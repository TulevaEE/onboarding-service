async function setTimeout() {
  const args = Array.prototype.slice.call(arguments);
  const handler = args.shift();
  const ms = args.shift();
  Utilities.sleep(ms);

  return handler.apply(this, args);
}

function Blob(parts, properties) {
  var joinedParts = parts.join("");
  var byteArray = Utilities.newBlob(joinedParts, properties.type, properties.name).getBytes();
  return new Uint8Array(byteArray);
}
