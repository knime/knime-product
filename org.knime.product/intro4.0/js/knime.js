window.updateTile = function (data) {
    var carousel = document.getElementById('carousel-content');

    var imagePlaceholder = carousel.getElementsByClassName('image-placeholder');
    for (var placeholderIndex = imagePlaceholder.length - 1; placeholderIndex >= 0; placeholderIndex--) {
        var contentDiv = document.getElementsByClassName('content');
        var imageSrc = data[placeholderIndex]['tile-image'];
        if (imageSrc) {
            var img = document.createElement('img');
            img.src = data[placeholderIndex]['tile-image'];
            imagePlaceholder[placeholderIndex].parentNode.insertBefore(img, contentDiv[placeholderIndex]);
        }
        imagePlaceholder[placeholderIndex].parentNode.removeChild(imagePlaceholder[placeholderIndex]);
    }
    
    var carouselTags = carousel.getElementsByClassName('line0');
    for (var tagIndex = carouselTags.length - 1; tagIndex >= 0; tagIndex--) {
        if (data[tagIndex]['tile-tag']) {
            var tileTag = document.createElement('p');
            tileTag.className = 'tile-tag';
            tileTag.innerHTML = data[tagIndex]['tile-tag'];
            carouselTags[tagIndex].appendChild(tileTag);
            carouselTags[tagIndex].className = 'tile-tag-container';
        } else {
            carouselTags[tagIndex].parentNode.removeChild(carouselTags[tagIndex]);
        }
    }

    var carouselTitles = carousel.getElementsByClassName('line1');
    for (var titleIndex = carouselTitles.length - 1; titleIndex >= 0; titleIndex--) {
        var title = document.createElement('p');
        title.className = 'tile-title';
        title.innerHTML = data[titleIndex]['tile-title'];
        carouselTitles[titleIndex].parentNode.appendChild(title);
        carouselTitles[titleIndex].parentNode.removeChild(carouselTitles[titleIndex]);
    }

    var carouselSubTitles = carousel.getElementsByClassName('line2');
    for (var subTitleIndex = carouselSubTitles.length - 1; subTitleIndex >= 0; subTitleIndex--) {
        var subtitle = document.createElement('p');
        subtitle.className = 'tile-text';
        if (data[subTitleIndex]['tile-text']) {
            subtitle.innerHTML = data[subTitleIndex]['tile-text'];
        }
        carouselSubTitles[subTitleIndex].parentNode.appendChild(subtitle);
        carouselSubTitles[subTitleIndex].parentNode.removeChild(carouselSubTitles[subTitleIndex]);
    }

    var carouselButtons = carousel.getElementsByClassName('button-placeholder');
    for (var buttonIndex = carouselButtons.length - 1; buttonIndex >= 0; buttonIndex--) {
        if (data[buttonIndex]['tile-link'] && data[buttonIndex]['tile-button-text']) {
            var button = document.createElement('div');
            button.className = 'button-div';
            var buttonTag = document.createElement('a');
            buttonTag.className = 'button-primary';
            buttonTag.href = data[buttonIndex]['tile-link'];
            buttonTag.innerHTML = data[buttonIndex]['tile-button-text'];
            carouselButtons[buttonIndex].parentNode.appendChild(button);
            // When there is a fresh workspace the button of the 'Get Started'-tile should be in yellow AP-14402
            if (data[buttonIndex]['tile-welcome-button-class']) {
                buttonTag.className += " " + data[buttonIndex]['tile-welcome-button-class'];
            }
            button.appendChild(buttonTag);
        }
        carouselButtons[buttonIndex].parentNode.removeChild(carouselButtons[buttonIndex]);
    }
};
window.hideElement = function (id) {
    document.getElementById(id).style.display = 'none';
};

window.displayUpdateTile = function (data) {
    var updateContainer = document.getElementById('update-container');
    if (updateContainer) {
        while (updateContainer.firstChild) {
            updateContainer.removeChild(updateContainer.firstChild);
        }
    } else {
        updateContainer = document.createElement('div');
        updateContainer.id = 'update-container';
    }

    var updateContent = document.createElement('div');
    updateContent.id = 'update-content';
    updateContent.className = 'inner-content';
    updateContainer.appendChild(updateContent);

    var updateBannerDiv = document.createElement('div');
    updateBannerDiv.id = 'update-banner';
    updateContent.appendChild(updateBannerDiv);

    var tileImage = document.createElement('img');
    tileImage.src = data.icon;
    updateBannerDiv.appendChild(tileImage);

    var tileText = document.createElement('p');
    tileText.className = 'update-text';
    tileText.innerHTML = data.tileContent;
    updateBannerDiv.appendChild(tileText);

    var updateText = document.createElement('p');
    updateText.className = 'update-title';
    updateText.innerHTML = data.title;
    updateBannerDiv.appendChild(updateText);

    var buttonDiv = document.createElement('div');
    buttonDiv.className = 'update-button';
    updateBannerDiv.appendChild(buttonDiv);

    var updateButton = document.createElement('a');
    updateButton.className = 'button-primary';
    updateButton.href = data.buttonAction;
    updateButton.innerHTML = data.buttonText;
    buttonDiv.appendChild(updateButton);

    var welcomePageWrapper = document.getElementById('inner-welcome-page');
    var contentContainer = document.getElementById('content-container');
    welcomePageWrapper.insertBefore(updateContainer, contentContainer);

    var titleWrapper = document.getElementById('title-wrapper');
    titleWrapper.setAttribute('class', 'update-shown');
    var contentContainer = document.getElementById('content-container');
    contentContainer.setAttribute('class', 'update-shown');
};

// prevent right click on the welcome page
document.addEventListener('contextmenu', function (event) { event.preventDefault(); });
