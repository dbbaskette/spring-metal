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
        $scope.chat = {
            messages: [],
            input: '',
            loading: false
        };

        $scope.openBoneyardChat = function() {
            console.log('Opening Boneyard chat...');
            ChatService.toggleChat();
        };

        $scope.sendMessage = function() {
            console.log('sendMessage called, input:', $scope.chat.input, 'loading:', $scope.chat.loading);
            if (!$scope.chat.input || !$scope.chat.input.trim() || $scope.chat.loading) {
                console.log('Message sending blocked - empty input or loading');
                return;
            }

            var userMessage = $scope.chat.input.trim();
            console.log('Sending message:', userMessage);
            $scope.chat.messages.push({role: 'user', text: userMessage});
            $scope.chat.input = '';
            $scope.chat.loading = true;

            $http.post('/ai/chat', {text: userMessage})
                .then(function(response) {
                    console.log('Received response:', response.data);
                    $scope.chat.messages.push({role: 'assistant', text: response.data.text});
                    $scope.chat.loading = false;
                    // Scroll to bottom
                    setTimeout(function() {
                        var messagesDiv = document.getElementById('chat-messages');
                        if (messagesDiv) messagesDiv.scrollTop = messagesDiv.scrollHeight;
                    }, 50);
                })
                .catch(function(error) {
                    console.error('Chat error:', error);
                    $scope.chat.messages.push({role: 'assistant', text: 'Sorry, I encountered an error. Please try again.'});
                    $scope.chat.loading = false;
                });
        };

        $scope.$on('chatVisibilityChanged', function(event, isVisible) {
            $scope.chatVisible = isVisible;
            if (isVisible && $scope.chat.messages.length === 0) {
                $scope.chat.messages.push({role: 'assistant', text: '🎸 Hi! I\'m your Boneyard assistant. Ask me anything about music!'});
            }
        });
    });
