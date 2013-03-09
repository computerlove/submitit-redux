"use strict";

$.urlParam = function(name){
    var results = new RegExp('[\\?&]' + name + '=([^&#]*)').exec(window.location.href);
    return results ? results[1] || 0 : 0;
}


function UpdateCtrl($scope,$http) {
	$scope.talk = {
		presentationType : "presentation",
		title: "",
		abstract: "",
		language: "no",
		level: "beginner",
		outline: "",
		highlight: "",
		equipment: "",
		talkTags: [],
		expectedAudience: "",
		speakers: [{
			speakerName: "",
			email: "",
			bio: "",
			picture: null,
			zipCode: "",
			givenId: null,
			dummyId: null
		}]
	};

	$scope.activePresentationClass = function(value) { 
		console.log($scope.talk.presentationType + "->" + value);
		return (value == $scope.talk.presentationType) ? "active" : "";
	};

	var talkid = $.urlParam("talkid");

	if (talkid != 0) {
		var jsonurl = "talkJson?talkid=" + talkid;


		$http({method: 'GET', url: jsonurl}).
	  		success(function(data, status, headers, config) {
	  			$scope.talk = data;
	  		}).
	  		error(function(data, status, headers, config) {
			    console.log("some error occured");
		  	});
	  	
	}



}