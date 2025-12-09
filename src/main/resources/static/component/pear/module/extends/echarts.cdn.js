layui.define(['echartsTheme'], function (exports) {
  var E = window.echarts;
  var T = layui.echartsTheme;
  if (E && T) {
    try { E.registerTheme('walden', T); } catch (e) {}
  }
  exports('echarts', E);
});
