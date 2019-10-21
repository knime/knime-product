var updateTile = function(data) {
	var carousel = document.getElementById("carousel-content");
	var tiles = carousel.childNodes;
	
	var imagePlaceholder = carousel.getElementsByClassName("image-placeholder");
	for (var i = imagePlaceholder.length-1; i >= 0; i--) {
		var contentDiv = document.getElementsByClassName("content");
		var img = document.createElement("img");
		img.src = data[i].imageLocation;
		imagePlaceholder[i].parentElement.insertBefore(img, contentDiv[i]);
		imagePlaceholder[i].remove();
	}
	
	var carouselTags = carousel.getElementsByClassName("line0");
	for (var i = carouselTags.length-1; i >= 0; i--) {
		var tag = document.createElement("p");
		tag.className = "tile-tag";
		tag.innerHTML = data[i].tagText;
		carouselTags[i].appendChild(tag);
		carouselTags[i].className = "tile-tag-container";
	}
	
	var carouselTitles = carousel.getElementsByClassName("line1");
	for (var i = carouselTitles.length-1; i >= 0; i--) {
		var title = document.createElement("p");
		title.className = "tile-title";
		title.innerHTML = data[i].titleText;
		carouselTitles[i].parentElement.appendChild(title);
		carouselTitles[i].remove();
	}
	
	var carouselSubTitles = carousel.getElementsByClassName("line2");
	for (var i = carouselSubTitles.length-1; i >= 0; i--) {
		var subtitle = document.createElement("p");
		subtitle.className = "tile-sub-title";
		subtitle.innerHTML = data[i].subTitleText;
		
		var subtitleTooltip = document.createElement('span');
		subtitleTooltip.className = 'tile-sub-title-tooltip';
		subtitleTooltip.innerHTML = data[i].subTitleText;
		subtitle.appendChild(subtitleTooltip);
		
		carouselSubTitles[i].parentElement.appendChild(subtitle);
		carouselSubTitles[i].remove();
	}
	
	var carouselButtons = carousel.getElementsByClassName("button-placeholder");
	for (var i = carouselButtons.length-1; i >= 0; i--) {
		var tag = document.createElement("a");
		tag.className = "button-primary";
		tag.href = data[i].buttonAction
		tag.innerHTML = data[i].buttonText;
		carouselButtons[i].appendChild(tag);
		carouselButtons[i].className = "button-div";
	}
}
var hideElement = function (id) {
	document.getElementById(id).style.display = "none";
}

var displayUpadteTile = function (data) {
	var updateContainer = document.createElement("div");
	updateContainer.id = 'update-container';
	
	var updateContent = document.createElement('div');
	updateContent.id = 'update-content';
	updateContent.className = 'inner-content';
	updateContainer.appendChild(updateContent);
	
	var carouselLength = document.createElement('div');
	carouselLength.id = 'carousel-length';
	carouselLength.className = 'carousel-length';
	updateContent.appendChild(carouselLength);
	
	var updateBannerDiv = document.createElement('div');
	updateBannerDiv.id = 'updateBanner';
	updateBannerDiv.className = 'update-tile';
	carouselLength.appendChild(updateBannerDiv);
	
	var tileImage = document.createElement('img');
	tileImage.src = data.icon;
	updateBannerDiv.appendChild(tileImage);
	
	var tileText = document.createElement('p');
	tileText.className = 'tile-text';
	tileText.innerHTML = data.tileContent;
	updateBannerDiv.appendChild(tileText);
	
	var updateText = document.createElement('p');
	updateText.className = 'tile-text';
	updateText.innerHTML = data.title;
	updateBannerDiv.appendChild(updateText);
	
	var buttonDiv = document.createElement('div')
	buttonDiv.className = 'button-div';
	updateBannerDiv.appendChild(buttonDiv);
	
	var updateButton = document.createElement('a');
	updateButton.className = 'button-primary';
	updateButton.href = data.buttonAction;
	updateButton.innerHTML = data.buttonText;
	buttonDiv.appendChild(updateButton);
	
	var welcomePageWrapper = document.getElementById('welcome-page-wrapper');
	var titleWrapper = document.getElementById('title-wrapper');
	titleWrapper.setAttribute("style", "background: #FFF");
	var contentContainer = document.getElementById('content-container');
	welcomePageWrapper.insertBefore(updateContainer, contentContainer);
}

// prevent right click on the welcome page
document.addEventListener('contextmenu', event => event.preventDefault());