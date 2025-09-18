angular.module('mcp', ['ngResource']).
    controller('McpController', function ($scope, $log, $http) {

        // Initialize scope variables
        $scope.mcpStatus = {};
        $scope.loading = false;
        $scope.statusMessage = '';
        $scope.statusType = 'info';
        $scope.configuredServers = [];
        $scope.newServer = {};

        // Initialize the controller
        $scope.init = function() {
            $log.info('Initializing Spring AI MCP Controller');
            $scope.loadMcpStatus();
            $scope.loadServers();
        };

        // Load MCP status from backend API
        $scope.loadMcpStatus = function() {
            $scope.loading = true;
            $scope.statusMessage = 'Loading MCP client status...';

            $http.get('/api/mcp/status')
                .then(function(response) {
                    var data = response.data;
                    $scope.mcpStatus = {
                        status: data.enabled ? 'MCP Enabled' : 'MCP Disabled',
                        message: data.message,
                        details: data.details,
                        enabled: data.enabled,
                        servers: data.servers || []
                    };

                    // Update configured servers list for display
                    $scope.configuredServers = data.servers.map(function(serverName) {
                        return {
                            name: serverName,
                            url: 'http://localhost:8090',
                            endpoint: '/api/mcp',
                            description: 'AudioDB MCP Server - Music database configured in application.yml'
                        };
                    });

                    $scope.loading = false;
                    $scope.statusMessage = '';
                })
                .catch(function(error) {
                    $log.error('Error loading MCP status:', error);
                    $scope.mcpStatus = {
                        status: 'Error loading MCP status',
                        message: error.data?.message || 'Unable to connect to MCP service'
                    };
                    $scope.showStatus('Error loading MCP status', 'error');
                    $scope.loading = false;
                });
        };

        // Show status message
        $scope.showStatus = function(message, type) {
            $scope.statusMessage = message;
            $scope.statusType = type || 'info';

            // Auto-hide after 5 seconds
            setTimeout(function() {
                $scope.$apply(function() {
                    $scope.statusMessage = '';
                });
            }, 5000);
        };

        // Test RAG with MCP integration
        $scope.testMcpRag = function() {
            $scope.loading = true;
            $scope.statusMessage = 'Testing RAG with MCP tool integration...';

            var testMessage = {
                messages: [{
                    text: "What albums did The Beatles release?"
                }]
            };

            $http.post('/ai/rag', testMessage)
                .then(function(response) {
                    $scope.showStatus('RAG with MCP tools test successful!', 'success');
                    $scope.ragResponse = response.data.text;
                    $scope.loading = false;
                })
                .catch(function(error) {
                    $log.error('Error testing RAG with MCP:', error);
                    $scope.showStatus('RAG test failed: ' + (error.data?.message || error.statusText), 'error');
                    $scope.loading = false;
                });
        };

        // Load configured servers (simplified for display only)
        $scope.loadServers = function() {
            // This is now handled in loadMcpStatus()
        };

        // Add new server (UI disabled - show message)
        $scope.addServer = function($event) {
            if ($event) {
                $event.preventDefault();
            }
            $scope.showStatus('Server management is now handled via application.yml configuration. Restart the application after making changes.', 'info');
        };

        // Remove server (UI disabled - show message)
        $scope.removeServer = function(serverName) {
            $scope.showStatus('AudioDB server is configured in application.yml and cannot be removed via UI. Modify application.yml to change server configuration.', 'info');
        };

        // Enable MCP (always enabled with auto-configuration)
        $scope.enableMcp = function() {
            $scope.showStatus('MCP is automatically enabled when tools are available. Check that the AudioDB server is running at http://localhost:8090/api/mcp', 'info');
        };

        // Disable MCP (not available with auto-configuration)
        $scope.disableMcp = function() {
            $scope.showStatus('MCP cannot be disabled when using Spring AI auto-configuration. Stop the MCP server or remove the profile to disable.', 'info');
        };

        // Refresh status
        $scope.refresh = function() {
            $scope.loadMcpStatus();
        };

    }).
    controller('ChatController', function ($scope, $log, $http) {

        $scope.init = function() {
            $log.info('Initializing Spring Metal Chat Controller');
        };

    });