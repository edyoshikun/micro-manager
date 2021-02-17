///////////////////////////////////////////////////////////////////////////////
// FILE:          BaslerAce.h
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Adapter for Basler  Cameras
//
// Copyright 2018 Henry Pinkard
//
// Redistribution and use in source and binary forms, with or without modification, 
// are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this 
// list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice, this
// list of conditions and the following disclaimer in the documentation and/or other 
// materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its contributors may
// be used to endorse or promote products derived from this software without specific 
// prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES 
// OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT 
// SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, 
// INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR 
// BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN 
// ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH 
// DAMAGE.


#pragma once

#include "DeviceBase.h"
#include "DeviceThreads.h"
#include <string>
#include <vector>
#include <map>
#include "ImageMetadata.h"
#include "ImgBuffer.h"
#include <iostream>
#include <pylon/PylonIncludes.h>


//////////////////////////////////////////////////////////////////////////////
// Error codes
//
//#define ERR_UNKNOWN_BINNING_MODE 410
enum
{
	ERR_SERIAL_NUMBER_REQUIRED = 20001,
	ERR_SERIAL_NUMBER_NOT_FOUND,
	ERR_CANNOT_CONNECT,
	PYLON_ERR = 10002
};

//////////////////////////////////////////////////////////////////////////////
// Basler camera class
//////////////////////////////////////////////////////////////////////////////
//Callback class for putting frames in circular buffer as they arrive

class CTempCameraEventHandler;
class CircularBufferInserter;
class BaslerCamera : public CCameraBase<BaslerCamera>  {
public:
	BaslerCamera();
	~BaslerCamera();

	// MMDevice API
	// ------------
	int Initialize();
	int Shutdown();

	void GetName(char* name) const;      
	bool Busy() {return false;}
	

	// MMCamera API
	// ------------
	int SnapImage();
	const unsigned char* GetImageBuffer();
	void* Buffer4ContinuesShot;
	
	unsigned  GetNumberOfComponents() const;
	unsigned GetImageWidth() const;
	unsigned GetImageHeight() const;
	unsigned GetImageBytesPerPixel() const;

	unsigned GetBitDepth() const;
	long GetImageBufferSize() const;
	double GetExposure() const;
	void SetExposure(double exp);
	int SetROI(unsigned x, unsigned y, unsigned xSize, unsigned ySize); 
	int GetROI(unsigned& x, unsigned& y, unsigned& xSize, unsigned& ySize); 
	int ClearROI();
	void ReduceImageSize(int64_t Width, int64_t Height);
	int GetBinning() const;
	int SetBinning(int binSize);
	int IsExposureSequenceable(bool& seq) const {seq = false; return DEVICE_OK;}
	void RGBPackedtoRGB(void* destbuffer, const CGrabResultPtr& ptrGrabResult);
	//int SetProperty(const char* name, const char* value);
	int CheckForBinningMode(CPropertyAction *pAct);
	void AddToLog(std::string msg);
	void CopyToImageBuffer(CGrabResultPtr image);
	CImageFormatConverter *converter;
    CircularBufferInserter *ImageHandler_;
	std::string EnumToString(EDeviceAccessiblityInfo DeviceAccessiblityInfo);
	void UpdateTemperature();

	/**
	* Starts continuous acquisition.
	*/
	int StartSequenceAcquisition(long numImages, double interval_ms, bool stopOnOverflow);
	int StartSequenceAcquisition(double interval_ms);
	int StopSequenceAcquisition();
	int PrepareSequenceAcqusition();

	/**
	* Flag to indicate whether Sequence Acquisition is currently running.
	* Return true when Sequence acquisition is active, false otherwise
	*/
	bool IsCapturing();

	//Genicam Callback
	void ResultingFramerateCallback(GenApi::INode* pNode);


	// action interface
	// ----------------
	int OnAcqFramerate(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAcqFramerateEnable(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAutoExpore(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnAutoGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnBinningMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnDeviceLinkThroughputLimit(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnHeight(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnInterPacketDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnLightSourcePreset(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnResultingFramerate(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnReverseX(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnReverseY(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnSensorReadoutMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnShutterMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTemperature(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTemperatureState(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnWidth(MM::PropertyBase* pProp, MM::ActionType eAct);

	// 
	int OnTriggerSource(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerSelector(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerActivation(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnTriggerOverlap(MM::PropertyBase* pProp, MM::ActionType eAct);
	int OnTriggerDelay(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnExposureMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnUserOutputSelector(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnUserOutputValue(MM::PropertyBase* pProp, MM::ActionType eAct);

	//int OnLineSelector(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnLineMode(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnLineInverter(MM::PropertyBase* pProp, MM::ActionType eAct);
	//int OnLineSource(MM::PropertyBase* pProp, MM::ActionType eAct);


private:

	CBaslerUniversalInstantCamera *camera_;
	CTempCameraEventHandler *pTempHandler_;

    int nComponents_;
	unsigned bitDepth_;
	bool  colorCamera_;

	unsigned maxWidth_, maxHeight_;
	int64_t DeviceLinkThroughputLimit_;
	int64_t InterPacketDelay_;
	double ResultingFrameRatePrevious;
	double acqFramerate_, acqFramerateMax_, acqFramerateMin_;
	double exposure_us_, exposureMax_, exposureMin_;
	double gain_, gainMax_, gainMin_;
	double offset_, offsetMin_, offsetMax_;


	std::string binningFactor_;
	std::string pixelType_;
	std::string reverseX_, reverseY_;
	std::string sensorReadoutMode_;
	std::string setAcqFrm_;
	std::string shutterMode_;
	std::string temperature_;
	std::string temperatureState_;
	

	void* imgBuffer_;
	long imgBufferSize_;
	ImgBuffer img_;
	INodeMap* nodeMap_;

	bool initialized_;

	//MM::MMTime startTime_;

	void ResizeSnapBuffer();

	//Create properties based on camera properties from FLIR since both are GenIcam Standards
	template<typename enumType>
	void CreatePropertyFromEnum(const std::string& name, Pylon::IEnumParameterT<enumType>& camProp,int (BaslerCamera::* fpt)(MM::PropertyBase* pProp, MM::ActionType eAct));
	void CreatePropertyFromFloat(const std::string& name,GenApi::IFloat& camProp,int (BaslerCamera::* fpt)(MM::PropertyBase* pProp, MM::ActionType eAct));
	
	template<typename enumType>
	int OnEnumPropertyChanged(Pylon::IEnumParameterT<enumType>& camProp,MM::PropertyBase* pProp,MM::ActionType eAct);
	int OnFloatPropertyChanged(GenApi::IFloat& camProp,MM::PropertyBase* pProp,MM::ActionType eAct);
	std::string EAccessName(GenApi::EAccessMode accessMode) {
		switch (accessMode) {
		case GenApi::EAccessMode::NA:
			return "Not available";
		case GenApi::EAccessMode::NI:
			return "Not Implemented";
		case GenApi::EAccessMode::RO:
			return "Read Only";
		case GenApi::EAccessMode::RW:
			return "Read Write";
		case GenApi::EAccessMode::WO:
			return "Write Only";
		default:
			return "Unknown";
		}
	}

};

//Enumeration used for distinguishing different events.
enum TemperatureEvents
{
	TempCritical = 100,
	TempOverTemp = 200    
};

// Number of images to be grabbed.
static const uint32_t c_countOfImagesToGrab = 5;


// Example handler for camera events.
class CTempCameraEventHandler : public CBaslerUniversalCameraEventHandler
{
private:
	BaslerCamera* dev_;
public:
	CTempCameraEventHandler(BaslerCamera* dev);
	virtual void OnCameraEvent(CBaslerUniversalInstantCamera& camera, intptr_t userProvidedId, GenApi::INode* pNode);
};


class CircularBufferInserter : public CImageEventHandler {
private:
	BaslerCamera* dev_;

public:
	CircularBufferInserter(BaslerCamera* dev);

	virtual void OnImageGrabbed( CInstantCamera& camera, const CGrabResultPtr& ptrGrabResult);
};


// Implementing template based on other GENICAM implamentations
template<typename enumType>
inline void BaslerCamera::CreatePropertyFromEnum(const std::string& name, Pylon::IEnumParameterT<enumType>& camProp, int(BaslerCamera::* fpt)(MM::PropertyBase* pProp, MM::ActionType eAct))
{
	auto accessMode = camProp.GetAccessMode();

	if (accessMode == GenApi::EAccessMode::RO || accessMode == GenApi::EAccessMode::RW || accessMode == GenApi::EAccessMode::NA)
	{
		try
		{
			bool readOnly = accessMode == GenApi::EAccessMode::RO || accessMode == GenApi::EAccessMode::NA;
			auto pAct = new CPropertyAction(this, fpt);

			if (accessMode != GenApi::EAccessMode::NA)
			{
				GenApi::StringList_t propertyValues;
				camProp.GetSymbolics(propertyValues);

				CreateProperty(name.c_str(), camProp.GetCurrentEntry()->GetSymbolic().c_str(), MM::String, readOnly, pAct);

				for (unsigned int i = 0; i < propertyValues.size(); i++)
					AddAllowedValue(name.c_str(), propertyValues[i].c_str());
			}
			else
			{
				CreateProperty(name.c_str(), "", MM::String, readOnly, pAct);
				AddAllowedValue(name.c_str(), "");
			}
		}
		catch (const GenericException& e)
		{
			// Error handling.
			AddToLog(e.GetDescription());
			cerr << "An exception occurred." << endl
				<< e.GetDescription() << endl;
		}
	}
	else
	{
		LogMessage(name + " property not created: Property not accessable\nAccess Mode: " + EAccessName(accessMode));
	}
}
template<typename enumType>
inline int BaslerCamera::OnEnumPropertyChanged(Pylon::IEnumParameterT<enumType>& camProp, MM::PropertyBase* pProp, MM::ActionType eAct)
{
	if (camProp.GetAccessMode() == GenApi::EAccessMode::NA)
		return DEVICE_OK;

	if (eAct == MM::BeforeGet)
	{
		try
		{
			auto mmProp = dynamic_cast<MM::Property*>(pProp);
			if (mmProp != nullptr) {
				mmProp->SetReadOnly(camProp.GetAccessMode() != GenApi::EAccessMode::RW);

				mmProp->ClearAllowedValues();
				GenApi::StringList_t propertyValues;
				camProp.GetSymbolics(propertyValues);
				for (unsigned int i = 0; i < propertyValues.size(); i++)
					mmProp->AddAllowedValue(propertyValues[i].c_str());
			}

			pProp->Set(camProp.GetCurrentEntry()->GetSymbolic());
		}
		catch (const GenericException & ex)
		{
			//// Error handling.
			//AddToLog(e.GetDescription());
			//cerr << "An exception occurred." << endl
			//	<< e.GetDescription() << endl;
			//return DEVICE_ERR;
			pProp->Set("");
		}
	}
	else if (eAct == MM::AfterSet)
	{
		try
		{
			std::string val;
			pProp->Get(val);
			camProp.FromString(GenICam::gcstring(val.c_str()));
		}
		catch (const GenericException & e)
		{
			// Error handling.
			AddToLog(e.GetDescription());
			cerr << "An exception occurred." << endl
				<< e.GetDescription() << endl;
			return DEVICE_ERR;
		}
	}
	return DEVICE_OK;
}