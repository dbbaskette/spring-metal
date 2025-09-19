angular.module('SpringMusic', ['albums', 'errors', 'status', 'info', 'mcp', 'ngRoute', 'ui.directives']).
    config(function ($locationProvider, $routeProvider) {
        // $locationProvider.html5Mode(true);

        $routeProvider.when('/errors', {
            controller: 'ErrorsController',
            templateUrl: 'templates/errors.html'
        });
        $routeProvider.when('/mcp-settings', {
            controller: 'McpController',
            templateUrl: 'templates/mcp-settings.html'
        });
        $routeProvider.otherwise({
            controller: 'AlbumsController',
            templateUrl: 'templates/albums.html'
        });
    }
);
