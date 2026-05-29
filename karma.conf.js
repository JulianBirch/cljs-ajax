module.exports = function(config) {
  const bundle = process.env.KARMA_BUNDLE || "target-test/browser-tests.js";
  const integrationBaseUrl = process.env.CLJS_AJAX_INTEGRATION_BASE_URL;
  const proxies = integrationBaseUrl
    ? {
        "/ajax": `${integrationBaseUrl}/ajax`,
        "/ajax-transit": `${integrationBaseUrl}/ajax-transit`,
        "/ajax-url": `${integrationBaseUrl}/ajax-url`,
        "/ajax-form-data": `${integrationBaseUrl}/ajax-form-data`,
        "/ajax-form-data-png": `${integrationBaseUrl}/ajax-form-data-png`,
        "/favicon.ico": `${integrationBaseUrl}/favicon.ico`,
      }
    : {};
  config.set({
    basePath: ".",
    frameworks: ["cljs-test"],
    files: [bundle],
    preprocessors: {},
    proxies,
    client: {
      args: ["shadow.test.karma.init"]
    },
    browsers: ["ChromeHeadless"],
    singleRun: true
  });
};
