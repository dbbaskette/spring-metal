angular.module('mcp', ['ngResource']).
    controller('McpController', function ($scope, $log, $http, $timeout) {

        $scope.mcpStatus = {};
        $scope.loading = false;
        $scope.statusMessage = '';
        $scope.statusType = 'info';
        $scope.configuredServers = [];
        $scope.newServer = { enabled: true };

        var hideStatusPromise;

        $scope.init = function() {
            $log.info('Initializing Spring AI MCP Controller');
            $scope.refresh();
        };

        $scope.refresh = function() {
            $scope.loading = true;
            $scope.statusMessage = 'Loading MCP settings...';
            $scope.statusType = 'info';

            $scope.loadMcpStatus()
                .finally(function() {
                    $scope.loadConnections()
                        .finally(function() {
                            $scope.loading = false;
                            if ($scope.statusMessage === 'Loading MCP settings...') {
                                $scope.statusMessage = '';
                            }
                        });
                });
        };

        $scope.loadMcpStatus = function() {
            return $http.get('/api/mcp/status')
                .then(function(response) {
                    var data = response.data || {};
                    var servers = Array.isArray(data.servers) ? data.servers : [];
                    var activeCount = servers.filter(function(server) {
                        return server.enabled && server.status === 'ACTIVE';
                    }).length;

                    var totalTools = data.toolCount || 0;
                    var toolDetails = totalTools > 0 ? ' (' + totalTools + ' tools available)' : '';

                    $scope.mcpStatus = {
                        status: data.enabled ? 'MCP Enabled' : 'MCP Disabled',
                        message: data.message || '',
                        details: servers.length > 0
                            ? activeCount + ' of ' + servers.length + ' server(s) active' + toolDetails
                            : 'No MCP servers registered yet',
                        enabled: !!data.enabled,
                        toolCount: data.toolCount,
                        servers: servers
                    };
                })
                .catch(function(error) {
                    $log.error('Error loading MCP status:', error);
                    $scope.mcpStatus = {
                        status: 'Error loading MCP status',
                        message: error?.data?.message || 'Unable to connect to MCP service',
                        enabled: false,
                        servers: []
                    };
                    $scope.showStatus('Error loading MCP status', 'error');
                });
        };

        $scope.loadConnections = function() {
            return $http.get('/api/mcp/connections')
                .then(function(response) {
                    $scope.configuredServers = (response.data || []).map(function(server) {
                        server.displayEndpoint = server.endpoint || '/api/mcp';
                        server.toolCount = server.toolCount || 0;
                        server.simplifiedTools = $scope.getSimplifiedToolNames(server.availableTools || []);
                        return server;
                    });
                })
                .catch(function(error) {
                    $log.error('Error loading MCP connections:', error);
                    $scope.configuredServers = [];
                    $scope.showStatus('Unable to load MCP connections', 'error');
                });
        };

        $scope.getSimplifiedToolNames = function(tools) {
            if (!Array.isArray(tools) || tools.length === 0) {
                return '';
            }

            var simplified = tools.map(function(tool) {
                if (typeof tool === 'string') {
                    return tool.replace(/^mcp__[^_]*__/, '').replace(/_/g, ' ');
                }
                if (tool.name) {
                    return tool.name.replace(/^mcp__[^_]*__/, '').replace(/_/g, ' ');
                }
                return 'unknown';
            });

            if (simplified.length <= 3) {
                return simplified.join(', ');
            }

            return simplified.slice(0, 3).join(', ') + ' (+' + (simplified.length - 3) + ' more)';
        };

        $scope.showStatus = function(message, type) {
            $scope.statusMessage = message;
            $scope.statusType = type || 'info';

            if (hideStatusPromise) {
                $timeout.cancel(hideStatusPromise);
            }

            hideStatusPromise = $timeout(function() {
                $scope.statusMessage = '';
            }, 5000);
        };

        function buildRequestPayload(source, overrides) {
            overrides = overrides || {};
            if (!source) {
                return { error: 'Missing server details.' };
            }

            var name = (source.name || '').trim();
            var baseUrl = (source.baseUrl || source.url || '').trim();
            var endpoint = (source.endpoint || '').trim();
            var enabled = overrides.enabled !== undefined ? overrides.enabled
                : (source.enabled !== undefined ? !!source.enabled : true);

            if (!name) {
                return { error: 'Server name is required.' };
            }
            if (!baseUrl) {
                return { error: 'Server base URL is required.' };
            }
            if (!endpoint) {
                return { error: 'Server endpoint is required.' };
            }

            var payload = {
                name: name,
                baseUrl: baseUrl,
                endpoint: endpoint,
                enabled: enabled
            };

            var headerSource = overrides.headers !== undefined ? overrides.headers : source.headers;
            if (headerSource) {
                if (typeof headerSource === 'string') {
                    if (headerSource.trim().length > 0) {
                        try {
                            payload.headers = JSON.parse(headerSource);
                        }
                        catch (err) {
                            return { error: 'Headers must be valid JSON.' };
                        }
                    }
                }
                else if (typeof headerSource === 'object') {
                    if (Object.keys(headerSource).length > 0) {
                        payload.headers = headerSource;
                    }
                }
            }

            return { payload: payload };
        }

        $scope.testNewServer = function() {
            var result = buildRequestPayload($scope.newServer, { enabled: true });
            if (result.error) {
                $scope.showStatus(result.error, 'error');
                return;
            }

            $scope.loading = true;
            $http.post('/api/mcp/connections/test', result.payload)
                .then(function(response) {
                    var data = response.data || {};
                    var message = data.success ? (data.message || 'Connection succeeded.')
                        : (data.message || 'Connection failed.');
                    $scope.showStatus(message, data.success ? 'success' : 'error');
                })
                .catch(function(error) {
                    $log.error('Error testing MCP connection:', error);
                    $scope.showStatus('Connection test failed: ' + (error?.data?.message || error.statusText), 'error');
                })
                .finally(function() {
                    $scope.loading = false;
                });
        };

        $scope.addServer = function($event) {
            if ($event) {
                $event.preventDefault();
            }

            var result = buildRequestPayload($scope.newServer, { enabled: true });
            if (result.error) {
                $scope.showStatus(result.error, 'error');
                return;
            }

            $scope.loading = true;
            $http.post('/api/mcp/connections', result.payload)
                .then(function(response) {
                    var server = response.data || result.payload;
                    $scope.showStatus('Added MCP server "' + server.name + '"', 'success');
                    $scope.newServer = { enabled: true };
                    return $scope.refresh();
                })
                .catch(function(error) {
                    $log.error('Error adding MCP server:', error);
                    $scope.showStatus('Unable to add server: ' + (error?.data?.message || error.statusText), 'error');
                })
                .finally(function() {
                    $scope.loading = false;
                });
        };

        $scope.testServer = function(server) {
            var result = buildRequestPayload(server, { headers: server.headers, enabled: server.enabled });
            if (result.error) {
                $scope.showStatus(result.error, 'error');
                return;
            }

            $scope.loading = true;
            $http.post('/api/mcp/connections/test', result.payload)
                .then(function(response) {
                    var data = response.data || {};
                    var message = data.success ? (data.message || 'Connection succeeded.')
                        : (data.message || 'Connection failed.');
                    $scope.showStatus('Test: ' + message, data.success ? 'success' : 'error');
                })
                .catch(function(error) {
                    $log.error('Error testing MCP server:', error);
                    $scope.showStatus('Connection test failed: ' + (error?.data?.message || error.statusText), 'error');
                })
                .finally(function() {
                    $scope.loading = false;
                });
        };

        $scope.toggleServer = function(server) {
            if (!server || !server.id) {
                return;
            }

            $scope.loading = true;

            if (server.enabled) {
                $http.post('/api/mcp/connections/' + server.id + '/disable')
                    .then(function() {
                        $scope.showStatus('Server "' + server.name + '" disabled.', 'info');
                        return $scope.refresh();
                    })
                    .catch(function(error) {
                        $log.error('Error disabling MCP server:', error);
                        $scope.showStatus('Unable to disable server: ' + (error?.data?.message || error.statusText), 'error');
                    })
                    .finally(function() {
                        $scope.loading = false;
                    });
            }
            else {
                var result = buildRequestPayload(server, { enabled: true });
                if (result.error) {
                    $scope.loading = false;
                    $scope.showStatus(result.error, 'error');
                    return;
                }

                $http.put('/api/mcp/connections/' + server.id, result.payload)
                    .then(function() {
                        $scope.showStatus('Server "' + server.name + '" enabled.', 'success');
                        return $scope.refresh();
                    })
                    .catch(function(error) {
                        $log.error('Error enabling MCP server:', error);
                        $scope.showStatus('Unable to enable server: ' + (error?.data?.message || error.statusText), 'error');
                    })
                    .finally(function() {
                        $scope.loading = false;
                    });
            }
        };

        $scope.deleteServer = function(server) {
            if (!server || !server.id) {
                return;
            }

            if (!window.confirm('Remove MCP server "' + server.name + '"? This will drop its tools immediately.')) {
                return;
            }

            $scope.loading = true;
            $http.delete('/api/mcp/connections/' + server.id)
                .then(function() {
                    $scope.showStatus('Server "' + server.name + '" removed.', 'success');
                    return $scope.refresh();
                })
                .catch(function(error) {
                    $log.error('Error removing MCP server:', error);
                    $scope.showStatus('Unable to remove server: ' + (error?.data?.message || error.statusText), 'error');
                })
                .finally(function() {
                    $scope.loading = false;
                });
        };

        $scope.testMcpRag = function() {
            $scope.loading = true;
            $scope.statusMessage = 'Testing RAG with MCP tool integration...';
            $scope.statusType = 'info';

            var testMessage = {
                messages: [{
                    text: 'What albums did The Beatles release?'
                }]
            };

            $http.post('/ai/rag', testMessage)
                .then(function(response) {
                    $scope.showStatus('RAG with MCP tools test successful!', 'success');
                    $scope.ragResponse = response.data.text;
                })
                .catch(function(error) {
                    $log.error('Error testing RAG with MCP:', error);
                    $scope.showStatus('RAG test failed: ' + (error?.data?.message || error.statusText), 'error');
                })
                .finally(function() {
                    $scope.loading = false;
                });
        };

        $scope.enableMcp = function() {
            $scope.showStatus('MCP is enabled automatically when servers are active.', 'info');
        };

        $scope.disableMcp = function() {
            $scope.showStatus('Disable a server or remove the MCP profile to stop tool discovery.', 'info');
        };

        $scope.$on('$destroy', function() {
            if (hideStatusPromise) {
                $timeout.cancel(hideStatusPromise);
            }
        });

    });
