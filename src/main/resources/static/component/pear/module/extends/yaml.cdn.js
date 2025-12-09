layui.define(function (exports) {
  var api = {
    parse: function (s) {
      try { return YAML && YAML.parse ? YAML.parse(s) : {}; } catch (e) { return {}; }
    },
    load: function (path) {
      try {
        var xhr = new XMLHttpRequest();
        xhr.open('GET', path, false);
        xhr.send(null);
        if (xhr.status >= 200 && xhr.status < 300) {
          return YAML && YAML.parse ? YAML.parse(xhr.responseText) : {};
        }
      } catch (e) {}
      return {};
    }
  };
  exports('yaml', api);
});
