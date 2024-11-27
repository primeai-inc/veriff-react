const {
  withPlugins,
  createRunOncePlugin,
  withProjectBuildGradle,
} = require('@expo/config-plugins');

const pkg = require('@veriff/react-native-sdk/package.json');

function getUpdatedProjectGradle(buildGradle) {
  const insertIndex = getInsertIndex(buildGradle);

  // Skip adding maven url if no insertion points found
  if (insertIndex !== -1) {
    const updatedBuildGradle = [
      buildGradle.slice(0, insertIndex),
      "maven { url 'https://cdn.veriff.me/android/' } //veriff\n        ",
      buildGradle.slice(insertIndex),
    ].join('');

    return updatedBuildGradle;
  }
  return buildGradle;
}

function getInsertIndex(buildGradle) {
  // legacy logic, left for backward compatability
  const mavenLocalIndex = buildGradle.indexOf('mavenLocal()');
  if (mavenLocalIndex !== -1) {
    console.log(`found "mavenLocal()" at index ${mavenLocalIndex}`);
    return mavenLocalIndex;
  }

  // logic to support new Expo build.gradle file structure
  const allprojectsIndex = buildGradle.indexOf('allprojects');
  if (allprojectsIndex !== -1) {
    console.log(`found "allprojects" at index ${allprojectsIndex}`);
    // repositories after or "inside" of allprojects
    const repositoriesIndex = buildGradle.indexOf(
      'repositories',
      allprojectsIndex,
    );
    if (repositoriesIndex !== -1) {
      console.log(`found "repositories" at index ${repositoriesIndex}`);
      // mavenCentral() after or "inside" of repositories
      const mavenCentralIndex = buildGradle.indexOf(
        'mavenCentral()',
        repositoriesIndex,
      );
      if (mavenCentralIndex !== -1) {
        console.log(`found "mavenCentral()" at index ${mavenCentralIndex}`);
        return mavenCentralIndex;
      }
    }
  }

  console.log('mavenLocal() or mavenCentral() not found!');

  return -1;
}

function withVeriffProjectGradle(expoConfig) {
  return withProjectBuildGradle(expoConfig, (config) => {
    config.modResults.contents = getUpdatedProjectGradle(
      config.modResults.contents,
    );
    return config;
  });
}

function withVeriff(expoConfig) {
  return withPlugins(expoConfig, [withVeriffProjectGradle]);
}

exports.default = createRunOncePlugin(withVeriff, pkg.name, pkg.version);
