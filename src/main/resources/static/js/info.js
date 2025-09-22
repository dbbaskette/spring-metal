angular.module('info', ['ngResource']).
    factory('Info', function ($resource) {
        return $resource('appinfo');
    }).
    factory('ChatService', function($rootScope) {
        var service = {
            chatVisible: false,
            toggleChat: function() {
                this.chatVisible = !this.chatVisible;
                $rootScope.$broadcast('chatVisibilityChanged', this.chatVisible);
                console.log('Chat visibility toggled to:', this.chatVisible);
            }
        };
        return service;
    }).
    controller('InfoController', function ($scope, Info, ChatService, $http) {
        $scope.info = Info.get();
        $scope.chatService = ChatService;

        var STORAGE_KEY = 'spring-metal-chat-history';
        var CONTEXT_STORAGE_KEY = 'spring-metal-conversation-context';

        $scope.chat = {
            messages: [],
            input: '',
            loading: false
        };
        $scope.conversationContext = [];

        $scope.init = function() {
            $scope.loadChatHistory();
        };

        $scope.loadChatHistory = function() {
            try {
                var stored = localStorage.getItem(STORAGE_KEY);
                if (stored) {
                    $scope.chat.messages = JSON.parse(stored);
                    console.log('Loaded chat history with', $scope.chat.messages.length, 'messages');
                } else {
                    $scope.chat.messages = [{
                        role: 'assistant',
                        text: '🎸 Hi! I\'m your Boneyard assistant. Ask me anything about music!',
                        timestamp: new Date().toISOString()
                    }];
                }

                var storedContext = localStorage.getItem(CONTEXT_STORAGE_KEY);
                if (storedContext) {
                    $scope.conversationContext = JSON.parse(storedContext);
                    console.log('Loaded conversation context with', $scope.conversationContext.length, 'messages');
                } else {
                    $scope.rebuildConversationContext();
                }
            } catch (error) {
                console.error('Error loading chat history:', error);
                $scope.initializeEmptyChat();
            }
        };

        $scope.rebuildConversationContext = function() {
            $scope.conversationContext = [];
            if ($scope.chat.messages && $scope.chat.messages.length > 1) {
                for (var i = 1; i < $scope.chat.messages.length; i++) {
                    var msg = $scope.chat.messages[i];
                    if (msg.role && msg.text && msg.text.trim().length > 0) {
                        $scope.conversationContext.push({
                            role: msg.role === 'assistant' ? 'assistant' : 'user',
                            content: msg.text.trim(),
                            timestamp: msg.timestamp || new Date().toISOString()
                        });
                    }
                }

                if ($scope.conversationContext.length > 10) {
                    $scope.conversationContext = $scope.conversationContext.slice(-10);
                }
                console.log('Rebuilt conversation context with', $scope.conversationContext.length, 'messages');
            }
        };

        $scope.saveChatHistory = function() {
            try {
                localStorage.setItem(STORAGE_KEY, JSON.stringify($scope.chat.messages));
                localStorage.setItem(CONTEXT_STORAGE_KEY, JSON.stringify($scope.conversationContext));
                console.log('Saved chat history and context');
            } catch (error) {
                console.error('Error saving chat history:', error);
            }
        };

        $scope.initializeEmptyChat = function() {
            $scope.chat.messages = [{
                role: 'assistant',
                text: '🎸 Hi! I\'m your Boneyard assistant. Ask me anything about music!',
                timestamp: new Date().toISOString()
            }];
            $scope.conversationContext = [];
        };

        $scope.openBoneyardChat = function() {
            console.log('Opening Boneyard chat...');
            // Only open chat if LLM is enabled
            if ($scope.info && $scope.info.llmEnabled) {
                ChatService.toggleChat();
            } else {
                console.warn('Chat is not available - LLM service not configured');
            }
        };

        $scope.sendMessage = function() {
            console.log('sendMessage called, input:', $scope.chat.input, 'loading:', $scope.chat.loading);

            // Check if LLM is enabled
            if (!$scope.info || !$scope.info.llmEnabled) {
                console.error('Cannot send message - LLM service not available');
                return;
            }

            if (!$scope.chat.input || !$scope.chat.input.trim() || $scope.chat.loading) {
                console.log('Message sending blocked - empty input or loading');
                return;
            }

            var userMessage = $scope.chat.input.trim();
            console.log('Sending message:', userMessage);

            var userMsgEntry = {
                role: 'user',
                text: userMessage,
                timestamp: new Date().toISOString()
            };
            $scope.chat.messages.push(userMsgEntry);

            $scope.conversationContext.push({
                role: 'user',
                content: userMessage,
                timestamp: new Date().toISOString()
            });

            $scope.chat.input = '';
            $scope.chat.loading = true;

            // Immediately scroll to show the user's message
            setTimeout(function() {
                var messagesDiv = document.getElementById('chat-messages');
                if (messagesDiv) messagesDiv.scrollTop = messagesDiv.scrollHeight;
            }, 10);

            var requestPayload = {
                text: userMessage
            };

            if ($scope.conversationContext.length > 0) {
                var contextToSend = $scope.conversationContext.slice(-6);
                requestPayload.conversationContext = contextToSend;
                console.log('Sending with conversation context:', contextToSend.length, 'messages');
                console.log('Context preview:', contextToSend.map(function(msg, i) {
                    return '[' + i + '] ' + msg.role + ': ' + (msg.content ? msg.content.substring(0, 30) + '...' : 'NO CONTENT');
                }));
            }

            $http.post('/ai/chat', requestPayload)
                .then(function(response) {
                    console.log('Received response:', response.data);
                    var assistantMsgEntry = {
                        role: 'assistant',
                        text: response.data.text,
                        timestamp: new Date().toISOString()
                    };
                    $scope.chat.messages.push(assistantMsgEntry);

                    $scope.conversationContext.push({
                        role: 'assistant',
                        content: response.data.text,
                        timestamp: new Date().toISOString()
                    });

                    if ($scope.conversationContext.length > 10) {
                        $scope.conversationContext = $scope.conversationContext.slice(-10);
                    }

                    $scope.saveChatHistory();
                    $scope.chat.loading = false;

                    setTimeout(function() {
                        var messagesDiv = document.getElementById('chat-messages');
                        if (messagesDiv) messagesDiv.scrollTop = messagesDiv.scrollHeight;
                    }, 50);
                })
                .catch(function(error) {
                    console.error('Chat error:', error);
                    var errorMsgEntry = {
                        role: 'assistant',
                        text: 'Sorry, I encountered an error. Please try again.',
                        timestamp: new Date().toISOString()
                    };
                    $scope.chat.messages.push(errorMsgEntry);
                    $scope.saveChatHistory();
                    $scope.chat.loading = false;
                });
        };

        $scope.$on('chatVisibilityChanged', function(event, isVisible) {
            $scope.chatVisible = isVisible;
            if (isVisible && $scope.chat.messages.length === 0) {
                $scope.initializeEmptyChat();
                $scope.saveChatHistory();
            }
        });

        $scope.init();
    });
