import music21
import json
import sys
import os
import multiprocessing

def validate_abc(abc_file_path):
    try:
        # Load and parse the ABC file
        tune = music21.converter.parse(abc_file_path)
        
        # Expand repeats if any
        try:
            expanded_tune = tune.expandRepeats()
        except Exception as e:
            expanded_tune = tune

        results = []
        
        # Process parts/voices
        for part in expanded_tune.parts:
            voice_data = []
            for element in part.flatten().notesAndRests:
                is_grace = element.duration.isGrace
                if element.isNote:
                    voice_data.append({
                        "type": "note",
                        "pitch": element.pitch.midi,
                        "duration": float(element.duration.quarterLength),
                        "isGrace": is_grace
                    })
                elif element.isChord:
                    voice_data.append({
                        "type": "chord",
                        "pitches": [p.midi for p in element.pitches],
                        "duration": float(element.duration.quarterLength),
                        "isGrace": is_grace
                    })
                elif element.isRest:
                    voice_data.append({
                        "type": "rest",
                        "duration": float(element.duration.quarterLength)
                    })
            results.append(voice_data)
            
        return results
    except Exception as e:
        return {"error": str(e)}

def worker_task(abc_file_path, output_file):
    # This runs in a worker process
    result = validate_abc(abc_file_path)
    with open(output_file, 'w') as out:
        json.dump(result, out, indent=2)
    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python3 m21_validator.py <abc_file_or_dir> [output_dir]")
        sys.exit(1)
        
    abc_path = sys.argv[1]
    output_dir = sys.argv[2] if len(sys.argv) > 2 else None

    if os.path.isdir(abc_path):
        if output_dir:
            if not os.path.exists(output_dir):
                os.makedirs(output_dir)
            
            abc_files = sorted([f for f in os.listdir(abc_path) if f.endswith(".abc")])
            print(f"Processing {len(abc_files)} files with fresh processes and 15s timeout...")
            
            # maxtasksperchild=1 ensures a new process for every file, avoiding music21 memory/state bloat
            with multiprocessing.Pool(processes=multiprocessing.cpu_count(), maxtasksperchild=1) as pool:
                results = []
                for f in abc_files:
                    abc_file_path = os.path.join(abc_path, f)
                    output_file = os.path.join(output_dir, f.replace(".abc", ".json"))
                    
                    if os.path.exists(output_file):
                        continue
                        
                    res = pool.apply_async(worker_task, (abc_file_path, output_file))
                    results.append((f, res, output_file))
                
                processed_count = 0
                for f, res, out_file in results:
                    try:
                        # Wait up to 15 seconds for each file
                        res.get(timeout=15)
                        processed_count += 1
                    except multiprocessing.TimeoutError:
                        print(f"TIMEOUT: {f} took too long to process. Marking as error.")
                        with open(out_file, 'w') as out:
                            json.dump({"error": "timeout"}, out)
                    except Exception as e:
                        print(f"ERROR: {f} failed with: {str(e)}")
                        with open(out_file, 'w') as out:
                            json.dump({"error": str(e)}, out)
                    
                    if (processed_count > 0 and processed_count % 10 == 0) or True: # Print every file for now to see progress
                         print(f"Progress: {processed_count + 790}/{len(abc_files)} ({f})")
                         sys.stdout.flush()
            
            print(f"Batch processing complete.")
        else:
            # Legacy batch mode (single object to stdout)
            batch_results = {}
            for f in os.listdir(abc_path):
                if f.endswith(".abc"):
                    batch_results[f] = validate_abc(os.path.join(abc_path, f))
            print(json.dumps(batch_results, indent=2))
    else:
        print(json.dumps(validate_abc(abc_path), indent=2))
