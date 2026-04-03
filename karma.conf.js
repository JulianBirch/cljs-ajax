module.exports = function(config) {
  config.set({
    basePath: ".",
    frameworks: ["cljs-test"],
    files: [
      "target-test/browser-tests.js"
    ],
    preprocessors: {},
    client: {
      args: ["shadow.test.karma.init"]
    },
    browsers: ["ChromeHeadless"],
    singleRun: true
  });
};
